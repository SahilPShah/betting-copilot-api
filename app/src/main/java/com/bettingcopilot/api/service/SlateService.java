package com.bettingcopilot.api.service;

import com.bettingcopilot.api.dto.NoBetDto;
import com.bettingcopilot.api.dto.PickDto;
import com.bettingcopilot.api.dto.SlateResponse;
import com.bettingcopilot.api.entity.Game;
import com.bettingcopilot.api.entity.Recommendation;
import com.bettingcopilot.api.entity.SlateRun;
import com.bettingcopilot.api.repository.GameRepository;
import com.bettingcopilot.api.repository.RecommendationRepository;
import com.bettingcopilot.api.repository.SlateRunRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SlateService {

    private final SlateRunRepository slateRunRepo;
    private final RecommendationRepository recRepo;
    private final GameRepository gameRepo;
    private final ObjectMapper objectMapper;

    public SlateService(
            SlateRunRepository slateRunRepo,
            RecommendationRepository recRepo,
            GameRepository gameRepo,
            ObjectMapper objectMapper) {
        this.slateRunRepo = slateRunRepo;
        this.recRepo = recRepo;
        this.gameRepo = gameRepo;
        this.objectMapper = objectMapper;
    }

    public SlateResponse getSlate(LocalDate date) {
        SlateRun run =
                slateRunRepo
                        .findByRunDate(date)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "No slate run found for date: " + date));

        List<Recommendation> recs = recRepo.findDeduplicatedBySlateRunId(run.getSlateRunId());

        Map<String, Game> gameMap = loadGamesById(recs);

        List<PickDto> picks = new ArrayList<>();
        List<NoBetDto> noBets = new ArrayList<>();

        for (Recommendation rec : recs) {
            if ("medium".equals(rec.getDecision())) {
                picks.add(toPickDto(rec, gameMap.get(rec.getGameId())));
            } else if ("no_bet".equals(rec.getDecision())) {
                NoBetDto nb = new NoBetDto();
                nb.setGameId(rec.getGameId());
                nb.setReason(rec.getNoBetReason());
                noBets.add(nb);
            } else {
                // Unknown decision value — ignore (forward compatibility)
            }
        }

        SlateResponse resp = new SlateResponse();
        resp.setRunDate(run.getRunDate());
        resp.setModelVersion(run.getModelVersion());
        resp.setGamesCount(run.getGamesCount());
        resp.setPicksCount(run.getPicksCount());
        resp.setRanAt(run.getRanAt());
        resp.setPicks(picks);
        resp.setNoBets(noBets);
        return resp;
    }

    private Map<String, Game> loadGamesById(List<Recommendation> recs) {
        Set<String> gameIds = new HashSet<>();
        for (Recommendation r : recs) {
            if (r.getGameId() != null) {
                gameIds.add(r.getGameId());
            }
        }

        Map<String, Game> gameMap = new HashMap<>();
        if (gameIds.isEmpty()) {
            return gameMap;
        }

        for (Game g : gameRepo.findAllById(gameIds)) {
            gameMap.put(g.getGameId(), g);
        }
        return gameMap;
    }

    private PickDto toPickDto(Recommendation rec, Game game) {
        PickDto dto = new PickDto();
        dto.setGameId(rec.getGameId());
        dto.setMarket(rec.getMarket());
        dto.setSide(rec.getSide());
        dto.setEdge(rec.getEdge());
        dto.setConfidence(rec.getConfidence());
        dto.setDecision(rec.getDecision());
        dto.setLlmExplanation(rec.getLlmExplanation());

        if (game != null) {
            dto.setHomeTeam(game.getHomeTeamId());
            dto.setAwayTeam(game.getAwayTeamId());
            dto.setFirstPitchUtc(game.getFirstPitchUtc());
        }

        if (rec.getContextSnapshot() != null) {
            try {
                JsonNode ctx = objectMapper.readTree(rec.getContextSnapshot());
                dto.setModelProb(ctx.path("model_prob").asDouble());
                dto.setImpliedProb(ctx.path("implied_prob").asDouble());
                dto.setOdds(ctx.path("american_odds").asInt());

                JsonNode hs = ctx.path("home_starter");
                if (!hs.isMissingNode() && !hs.isNull()) {
                    dto.setHomeStarter(hs.path("name").asText(null));
                    dto.setHomeStarterEra(hs.path("era").asDouble());
                    dto.setHomeStarterL3Era(hs.path("l3_era").asDouble());
                    dto.setHomeStarterWhip(hs.path("whip").asDouble());
                }

                JsonNode as = ctx.path("away_starter");
                if (!as.isMissingNode() && !as.isNull()) {
                    dto.setAwayStarter(as.path("name").asText(null));
                    dto.setAwayStarterEra(as.path("era").asDouble());
                    dto.setAwayStarterL3Era(as.path("l3_era").asDouble());
                    dto.setAwayStarterWhip(as.path("whip").asDouble());
                }
            } catch (JacksonException ignored) {
                // Older record shape — leave fields null
            }
        }

        return dto;
    }
}
