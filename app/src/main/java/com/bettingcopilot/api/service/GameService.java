package com.bettingcopilot.api.service;

import com.bettingcopilot.api.dto.GameDetailResponse;
import com.bettingcopilot.api.dto.OddsDto;
import com.bettingcopilot.api.dto.OddsLineDto;
import com.bettingcopilot.api.dto.PredictionDto;
import com.bettingcopilot.api.dto.RecommendationSummaryDto;
import com.bettingcopilot.api.dto.StarterDto;
import com.bettingcopilot.api.dto.StartersDto;
import com.bettingcopilot.api.entity.Game;
import com.bettingcopilot.api.entity.OddsSnapshot;
import com.bettingcopilot.api.repository.GameRepository;
import com.bettingcopilot.api.repository.OddsSnapshotRepository;
import com.bettingcopilot.api.repository.PredictionRepository;
import com.bettingcopilot.api.repository.RecommendationRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GameService {

    private final GameRepository gameRepo;
    private final PredictionRepository predictionRepo;
    private final OddsSnapshotRepository oddsRepo;
    private final RecommendationRepository recRepo;
    private final ObjectMapper objectMapper;

    public GameService(
            GameRepository gameRepo,
            PredictionRepository predictionRepo,
            OddsSnapshotRepository oddsRepo,
            RecommendationRepository recRepo,
            ObjectMapper objectMapper) {
        this.gameRepo = gameRepo;
        this.predictionRepo = predictionRepo;
        this.oddsRepo = oddsRepo;
        this.recRepo = recRepo;
        this.objectMapper = objectMapper;
    }

    public GameDetailResponse getGame(String gameId) {
        Game game =
                gameRepo.findById(gameId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Game not found: " + gameId));

        GameDetailResponse resp = new GameDetailResponse();
        resp.setGameId(game.getGameId());
        resp.setGameDate(game.getGameDate());
        resp.setStatus(game.getStatus());
        resp.setHomeTeam(game.getHomeTeamId());
        resp.setAwayTeam(game.getAwayTeamId());
        resp.setFirstPitchUtc(game.getFirstPitchUtc());

        predictionRepo
                .findByGameId(gameId)
                .ifPresent(
                        p -> {
                            PredictionDto dto = new PredictionDto();
                            dto.setHomeWinProb(p.getHomeWinProb());
                            dto.setAwayWinProb(p.getAwayWinProb());
                            dto.setPredictedMargin(p.getPredictedMargin());
                            dto.setPredictedTotal(p.getPredictedTotal());
                            dto.setHomeCoverProb(p.getHomeCoverProb());
                            dto.setAwayCoverProb(p.getAwayCoverProb());
                            dto.setEloDiff(p.getEloDiff());
                            resp.setPrediction(dto);
                        });

        resp.setOdds(toOddsDto(oddsRepo.findLatestByGameId(gameId)));

        // Starters come from the most recent recommendation's context snapshot (any decision)
        recRepo.findTopByGameIdOrderByCreatedAtDesc(gameId)
                .ifPresent(rec -> resp.setStarters(parseStarters(rec.getContextSnapshot())));

        // Recommendation summary only exists when a pick was selected
        recRepo.findTopByGameIdAndDecisionOrderByCreatedAtDesc(gameId, "medium")
                .ifPresent(
                        rec -> {
                            RecommendationSummaryDto dto = new RecommendationSummaryDto();
                            dto.setMarket(rec.getMarket());
                            dto.setSide(rec.getSide());
                            dto.setEdge(rec.getEdge());
                            dto.setConfidence(rec.getConfidence());
                            dto.setDecision(rec.getDecision());
                            dto.setLlmExplanation(rec.getLlmExplanation());
                            resp.setRecommendation(dto);
                        });

        return resp;
    }

    OddsDto toOddsDto(List<OddsSnapshot> snapshots) {
        OddsDto dto = new OddsDto();
        Map<String, OddsLineDto> moneyline = new HashMap<>();
        Map<String, OddsLineDto> runLine = new HashMap<>();

        for (OddsSnapshot s : snapshots) {
            OddsLineDto line = new OddsLineDto();
            line.setAmericanOdds(s.getAmericanOdds());
            line.setImpliedProb(s.getImpliedProb());
            line.setPoint(s.getRunLinePoint());

            if ("moneyline".equals(s.getMarket())) {
                moneyline.put(s.getSide(), line);
            } else if ("run_line".equals(s.getMarket())) {
                runLine.put(s.getSide(), line);
            }
        }

        dto.setMoneyline(moneyline);
        dto.setRunLine(runLine);
        return dto;
    }

    StartersDto parseStarters(String contextSnapshot) {
        if (contextSnapshot == null) {
            return null;
        }
        try {
            JsonNode ctx = objectMapper.readTree(contextSnapshot);
            StarterDto home = parseStarter(ctx.path("home_starter"));
            StarterDto away = parseStarter(ctx.path("away_starter"));
            if (home == null && away == null) {
                return null;
            }
            StartersDto dto = new StartersDto();
            dto.setHome(home);
            dto.setAway(away);
            return dto;
        } catch (JacksonException e) {
            // Older record shape — no starter data available
            return null;
        }
    }

    private StarterDto parseStarter(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        StarterDto dto = new StarterDto();
        dto.setName(node.path("name").asText(null));
        dto.setEra(node.path("era").asDouble());
        dto.setL3Era(node.path("l3_era").asDouble());
        dto.setWhip(node.path("whip").asDouble());
        return dto;
    }
}
