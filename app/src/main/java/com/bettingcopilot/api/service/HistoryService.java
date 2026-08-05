package com.bettingcopilot.api.service;

import com.bettingcopilot.api.dto.HistoryPickDto;
import com.bettingcopilot.api.dto.HistoryResponse;
import com.bettingcopilot.api.dto.HistorySummaryDto;
import com.bettingcopilot.api.dto.PaginationDto;
import com.bettingcopilot.api.entity.Game;
import com.bettingcopilot.api.entity.Recommendation;
import com.bettingcopilot.api.repository.GameRepository;
import com.bettingcopilot.api.repository.RecommendationRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class HistoryService {

    private final RecommendationRepository recRepo;
    private final GameRepository gameRepo;
    private final ObjectMapper objectMapper;

    public HistoryService(
            RecommendationRepository recRepo, GameRepository gameRepo, ObjectMapper objectMapper) {
        this.recRepo = recRepo;
        this.gameRepo = gameRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public HistoryResponse getHistory(
            LocalDate startDate, LocalDate endDate, int page, int perPage) {
        // The summary spans the full date range while picks are paginated, so fetch the whole
        // range and page in memory — pick volumes are a handful of rows per day.
        List<Recommendation> allRecs =
                recRepo.findHistoryPicks(startDate, endDate, Integer.MAX_VALUE, 0);

        Map<String, Game> gameMap = loadGamesById(allRecs);

        HistorySummaryDto summary = new HistorySummaryDto();
        List<HistoryPickDto> allPicks = new ArrayList<>();
        for (Recommendation rec : allRecs) {
            Game game = gameMap.get(rec.getGameId());
            HistoryPickDto dto = toHistoryPickDto(rec, game);
            switch (dto.getOutcome() == null ? "PENDING" : dto.getOutcome()) {
                case "WIN" -> summary.setWins(summary.getWins() + 1);
                case "LOSS" -> summary.setLosses(summary.getLosses() + 1);
                case "PENDING" -> summary.setPending(summary.getPending() + 1);
                default -> summary.setPushes(summary.getPushes() + 1);
            }
            allPicks.add(dto);
        }

        int resolved = summary.getWins() + summary.getLosses();
        summary.setWinRate(resolved > 0 ? (double) summary.getWins() / resolved : null);

        int fromIndex = Math.min((page - 1) * perPage, allPicks.size());
        int toIndex = Math.min(fromIndex + perPage, allPicks.size());

        PaginationDto pagination = new PaginationDto();
        pagination.setPage(page);
        pagination.setPerPage(perPage);
        pagination.setTotal(allPicks.size());

        HistoryResponse resp = new HistoryResponse();
        resp.setSummary(summary);
        resp.setPicks(allPicks.subList(fromIndex, toIndex));
        resp.setPagination(pagination);
        return resp;
    }

    private HistoryPickDto toHistoryPickDto(Recommendation rec, Game game) {
        HistoryPickDto dto = new HistoryPickDto();
        dto.setGameId(rec.getGameId());
        dto.setRunDate(rec.getSlateRun() != null ? rec.getSlateRun().getRunDate() : null);
        dto.setMarket(rec.getMarket());
        dto.setSide(rec.getSide());
        dto.setEdge(rec.getEdge());
        dto.setConfidence(rec.getConfidence());
        dto.setDecision(rec.getDecision());

        if (rec.getContextSnapshot() != null) {
            try {
                JsonNode ctx = objectMapper.readTree(rec.getContextSnapshot());
                if (ctx.hasNonNull("american_odds")) {
                    dto.setOdds(ctx.path("american_odds").asInt());
                }
            } catch (JacksonException ignored) {
                // Older record shape — leave odds null
            }
        }

        if (game != null) {
            dto.setHomeTeam(game.getHomeTeamId());
            dto.setAwayTeam(game.getAwayTeamId());
            dto.setHomeScore(game.getHomeScore());
            dto.setAwayScore(game.getAwayScore());
            dto.setOutcome(
                    computeOutcome(
                            rec.getMarket(),
                            rec.getSide(),
                            game.getHomeScore(),
                            game.getAwayScore(),
                            game.getStatus()));
        } else {
            dto.setOutcome("PENDING");
        }
        return dto;
    }

    static String computeOutcome(
            String market, String side, Integer homeScore, Integer awayScore, String status) {
        if (!"final".equals(status) || homeScore == null || awayScore == null) {
            return "PENDING";
        }
        if ("moneyline".equals(market)) {
            boolean homeWon = homeScore > awayScore;
            return ("home".equals(side) == homeWon) ? "WIN" : "LOSS";
        }
        if ("run_line".equals(market)) {
            double margin = homeScore - awayScore;
            boolean homeCovered = margin > 1.5;
            return ("home".equals(side) == homeCovered) ? "WIN" : "LOSS";
        }
        return "PENDING";
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
}
