package com.bettingcopilot.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bettingcopilot.api.entity.Game;
import com.bettingcopilot.api.entity.OddsSnapshot;
import com.bettingcopilot.api.entity.Prediction;
import com.bettingcopilot.api.entity.Recommendation;
import com.bettingcopilot.api.repository.GameRepository;
import com.bettingcopilot.api.repository.OddsSnapshotRepository;
import com.bettingcopilot.api.repository.PredictionRepository;
import com.bettingcopilot.api.repository.RecommendationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GameServiceTest {

    private static final String GAME_ID = "2026-04-29-CLE-TBR-1";

    private static final String FULL_CONTEXT =
            """
        {
          "edge": 0.110,
          "bookmaker": "draftkings",
          "model_prob": 0.584,
          "implied_prob": 0.474,
          "american_odds": 102,
          "home_starter": {"name": "Gavin Williams", "era": 3.90, "l3_era": 4.50, "whip": 1.167},
          "away_starter": {"name": "Drew Rasmussen", "era": 3.21, "l3_era": 3.67, "whip": 0.922}
        }
        """;

    @Mock GameRepository gameRepo;

    @Mock PredictionRepository predictionRepo;

    @Mock OddsSnapshotRepository oddsRepo;

    @Mock RecommendationRepository recRepo;

    private GameService service() {
        return new GameService(gameRepo, predictionRepo, oddsRepo, recRepo, new ObjectMapper());
    }

    private Game game() {
        Game game = new Game();
        game.setGameId(GAME_ID);
        game.setGameDate(LocalDate.of(2026, 4, 29));
        game.setStatus("scheduled");
        game.setHomeTeamId("TBR");
        game.setAwayTeamId("CLE");
        return game;
    }

    private OddsSnapshot snapshot(String market, String side, int odds, Double point) {
        OddsSnapshot s = new OddsSnapshot();
        s.setSnapshotId(UUID.randomUUID());
        s.setGameId(GAME_ID);
        s.setMarket(market);
        s.setSide(side);
        s.setAmericanOdds(odds);
        s.setRunLinePoint(point);
        s.setImpliedProb(0.5);
        return s;
    }

    @Test
    void getGame_fullResponse_allFieldsMapped() {
        Prediction prediction = new Prediction();
        prediction.setGameId(GAME_ID);
        prediction.setHomeWinProb(0.416);
        prediction.setAwayWinProb(0.584);
        prediction.setEloDiff(-22.1);

        Recommendation rec = new Recommendation();
        rec.setRecId(UUID.randomUUID());
        rec.setGameId(GAME_ID);
        rec.setMarket("moneyline");
        rec.setSide("away");
        rec.setEdge(0.110);
        rec.setConfidence(7.3);
        rec.setDecision("medium");
        rec.setContextSnapshot(FULL_CONTEXT);
        rec.setLlmExplanation("explanation");

        when(gameRepo.findById(GAME_ID)).thenReturn(Optional.of(game()));
        when(predictionRepo.findByGameId(GAME_ID)).thenReturn(Optional.of(prediction));
        when(oddsRepo.findLatestByGameId(GAME_ID))
                .thenReturn(
                        List.of(
                                snapshot("moneyline", "home", -122, null),
                                snapshot("moneyline", "away", 102, null)));
        when(recRepo.findTopByGameIdOrderByCreatedAtDesc(GAME_ID)).thenReturn(Optional.of(rec));
        when(recRepo.findTopByGameIdAndDecisionOrderByCreatedAtDesc(GAME_ID, "medium"))
                .thenReturn(Optional.of(rec));

        var resp = service().getGame(GAME_ID);

        assertEquals(GAME_ID, resp.getGameId());
        assertEquals("TBR", resp.getHomeTeam());
        assertEquals("CLE", resp.getAwayTeam());
        assertNotNull(resp.getPrediction());
        assertEquals(0.584, resp.getPrediction().getAwayWinProb());
        assertEquals(-22.1, resp.getPrediction().getEloDiff());
        assertNotNull(resp.getOdds());
        assertEquals(-122, resp.getOdds().getMoneyline().get("home").getAmericanOdds());
        assertNotNull(resp.getStarters());
        assertEquals("Gavin Williams", resp.getStarters().getHome().getName());
        assertEquals(0.922, resp.getStarters().getAway().getWhip());
        assertNotNull(resp.getRecommendation());
        assertEquals("away", resp.getRecommendation().getSide());
        assertEquals("explanation", resp.getRecommendation().getLlmExplanation());
    }

    @Test
    void getGame_notFound_throws404() {
        when(gameRepo.findById("bad-id")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service().getGame("bad-id"));
    }

    @Test
    void getGame_noRecommendation_recommendationIsNull() {
        when(gameRepo.findById(GAME_ID)).thenReturn(Optional.of(game()));
        when(predictionRepo.findByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(oddsRepo.findLatestByGameId(GAME_ID)).thenReturn(List.of());
        when(recRepo.findTopByGameIdOrderByCreatedAtDesc(GAME_ID)).thenReturn(Optional.empty());
        when(recRepo.findTopByGameIdAndDecisionOrderByCreatedAtDesc(GAME_ID, "medium"))
                .thenReturn(Optional.empty());

        var resp = service().getGame(GAME_ID);

        assertNull(resp.getRecommendation());
        assertNull(resp.getStarters());
        assertNull(resp.getPrediction());
    }

    @Test
    void getGame_noMediumRecommendation_startersStillParsed() {
        Recommendation noBet = new Recommendation();
        noBet.setRecId(UUID.randomUUID());
        noBet.setGameId(GAME_ID);
        noBet.setDecision("no_bet");
        noBet.setContextSnapshot(FULL_CONTEXT);

        when(gameRepo.findById(GAME_ID)).thenReturn(Optional.of(game()));
        when(predictionRepo.findByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(oddsRepo.findLatestByGameId(GAME_ID)).thenReturn(List.of());
        when(recRepo.findTopByGameIdOrderByCreatedAtDesc(GAME_ID)).thenReturn(Optional.of(noBet));
        when(recRepo.findTopByGameIdAndDecisionOrderByCreatedAtDesc(GAME_ID, "medium"))
                .thenReturn(Optional.empty());

        var resp = service().getGame(GAME_ID);

        assertNull(resp.getRecommendation());
        assertNotNull(resp.getStarters());
        assertEquals("Drew Rasmussen", resp.getStarters().getAway().getName());
    }

    @Test
    void toOddsDto_groupsMoneylineAndRunLine() {
        var dto =
                service()
                        .toOddsDto(
                                List.of(
                                        snapshot("moneyline", "home", -122, null),
                                        snapshot("moneyline", "away", 102, null),
                                        snapshot("run_line", "home", 169, -1.5),
                                        snapshot("run_line", "away", -206, 1.5)));

        assertEquals(-122, dto.getMoneyline().get("home").getAmericanOdds());
        assertEquals(102, dto.getMoneyline().get("away").getAmericanOdds());
        assertEquals(-1.5, dto.getRunLine().get("home").getPoint());
        assertEquals(1.5, dto.getRunLine().get("away").getPoint());
    }

    @Test
    void parseStarters_missingFields_returnsNull() {
        var service = service();

        assertNull(service.parseStarters(null));
        assertNull(service.parseStarters("{\"model_prob\": 0.5}"));
        assertNull(service.parseStarters("not-json"));
    }
}
