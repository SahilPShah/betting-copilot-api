package com.bettingcopilot.api.service;

import static com.bettingcopilot.api.service.HistoryService.computeOutcome;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

import com.bettingcopilot.api.entity.Game;
import com.bettingcopilot.api.entity.Recommendation;
import com.bettingcopilot.api.entity.SlateRun;
import com.bettingcopilot.api.repository.GameRepository;
import com.bettingcopilot.api.repository.RecommendationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class HistoryServiceTest {

    @Mock RecommendationRepository recRepo;

    @Mock GameRepository gameRepo;

    private HistoryService service() {
        return new HistoryService(recRepo, gameRepo, new ObjectMapper());
    }

    // --- computeOutcome: pure logic ---

    @Test
    void moneyline_win_homeSide() {
        assertEquals("WIN", computeOutcome("moneyline", "home", 4, 2, "final"));
    }

    @Test
    void moneyline_loss_homeSide() {
        assertEquals("LOSS", computeOutcome("moneyline", "home", 2, 4, "final"));
    }

    @Test
    void moneyline_win_awaySide() {
        assertEquals("WIN", computeOutcome("moneyline", "away", 2, 4, "final"));
    }

    @Test
    void runLine_win_homeSide() {
        assertEquals("WIN", computeOutcome("run_line", "home", 4, 2, "final"));
    }

    @Test
    void runLine_loss_homeSide_marginOne() {
        assertEquals("LOSS", computeOutcome("run_line", "home", 3, 2, "final"));
    }

    @Test
    void runLine_win_awaySide() {
        assertEquals("WIN", computeOutcome("run_line", "away", 3, 2, "final"));
    }

    @Test
    void pending_scheduledGame() {
        assertEquals("PENDING", computeOutcome("moneyline", "home", null, null, "scheduled"));
    }

    @Test
    void pending_nullScores() {
        assertEquals("PENDING", computeOutcome("moneyline", "home", null, null, "final"));
    }

    // --- service-level ---

    private Recommendation rec(String gameId, LocalDate runDate) {
        SlateRun run = new SlateRun();
        run.setSlateRunId(UUID.randomUUID());
        run.setRunDate(runDate);

        Recommendation rec = new Recommendation();
        rec.setRecId(UUID.randomUUID());
        rec.setGameId(gameId);
        rec.setSlateRun(run);
        rec.setMarket("moneyline");
        rec.setSide("home");
        rec.setDecision("medium");
        rec.setEdge(0.1);
        rec.setConfidence(7.0);
        rec.setContextSnapshot("{\"american_odds\": -136}");
        return rec;
    }

    private Game game(String gameId, String status, Integer homeScore, Integer awayScore) {
        Game game = new Game();
        game.setGameId(gameId);
        game.setStatus(status);
        game.setHomeTeamId("TBR");
        game.setAwayTeamId("CLE");
        game.setHomeScore(homeScore);
        game.setAwayScore(awayScore);
        return game;
    }

    @Test
    void getHistory_winRateCalculation() {
        LocalDate date = LocalDate.of(2026, 4, 22);
        List<Recommendation> recs =
                List.of(
                        rec("g1", date),
                        rec("g2", date),
                        rec("g3", date),
                        rec("g4", date),
                        rec("g5", date),
                        rec("g6", date));
        List<Game> games =
                List.of(
                        game("g1", "final", 4, 2),
                        game("g2", "final", 5, 1),
                        game("g3", "final", 3, 0),
                        game("g4", "final", 1, 6),
                        game("g5", "scheduled", null, null),
                        game("g6", "scheduled", null, null));

        when(recRepo.findHistoryPicks(any(), any(), anyInt(), anyInt())).thenReturn(recs);
        when(gameRepo.findAllById(anyIterable())).thenReturn(games);

        var resp = service().getHistory(date, date, 1, 20);

        assertEquals(3, resp.getSummary().getWins());
        assertEquals(1, resp.getSummary().getLosses());
        assertEquals(2, resp.getSummary().getPending());
        assertEquals(0.75, resp.getSummary().getWinRate());
        assertEquals(-136, resp.getPicks().getFirst().getOdds());
        assertEquals(date, resp.getPicks().getFirst().getRunDate());
    }

    @Test
    void getHistory_noResolvedPicks_winRateIsNull() {
        LocalDate date = LocalDate.of(2026, 4, 22);
        when(recRepo.findHistoryPicks(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(rec("g1", date)));
        when(gameRepo.findAllById(anyIterable()))
                .thenReturn(List.of(game("g1", "scheduled", null, null)));

        var resp = service().getHistory(date, date, 1, 20);

        assertNull(resp.getSummary().getWinRate());
        assertEquals(1, resp.getSummary().getPending());
    }

    @Test
    void getHistory_paginationMath() {
        LocalDate date = LocalDate.of(2026, 4, 22);
        List<Recommendation> recs =
                List.of(
                        rec("g1", date),
                        rec("g2", date),
                        rec("g3", date),
                        rec("g4", date),
                        rec("g5", date));
        when(recRepo.findHistoryPicks(any(), any(), anyInt(), anyInt())).thenReturn(recs);
        when(gameRepo.findAllById(anyIterable())).thenReturn(List.of());

        var resp = service().getHistory(date, date, 2, 2);

        assertEquals(2, resp.getPicks().size());
        assertEquals("g3", resp.getPicks().getFirst().getGameId());
        assertEquals(2, resp.getPagination().getPage());
        assertEquals(2, resp.getPagination().getPerPage());
        assertEquals(5, resp.getPagination().getTotal());
        // Summary spans the whole range, not just the page
        assertEquals(5, resp.getSummary().getPending());
    }

    @Test
    void getHistory_pageBeyondEnd_returnsEmptyPicks() {
        LocalDate date = LocalDate.of(2026, 4, 22);
        when(recRepo.findHistoryPicks(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(rec("g1", date)));
        when(gameRepo.findAllById(anyIterable())).thenReturn(List.of());

        var resp = service().getHistory(date, date, 5, 20);

        assertEquals(0, resp.getPicks().size());
        assertEquals(1, resp.getPagination().getTotal());
    }
}
