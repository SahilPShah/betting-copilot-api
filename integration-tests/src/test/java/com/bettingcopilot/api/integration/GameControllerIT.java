package com.bettingcopilot.api.integration;

import com.bettingcopilot.api.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@Tag("integration")
class GameControllerIT extends AbstractIntegrationTest {

    private static final String GAME_ID = "2026-04-29-CLE-TBR-1";

    private static final String CONTEXT =
            """
        {"edge": 0.110, "model_prob": 0.584, "implied_prob": 0.474, "american_odds": 102,
         "home_starter": {"name": "Gavin Williams", "era": 3.90, "l3_era": 4.50, "whip": 1.167},
         "away_starter": {"name": "Drew Rasmussen", "era": 3.21, "l3_era": 3.67, "whip": 0.922}}
        """;

    @Autowired RestTestClient client;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    private void seedGameWithOdds() {
        insertGame(GAME_ID, LocalDate.of(2026, 4, 29), "TBR", "CLE", "scheduled", null, null);
        insertPrediction(GAME_ID, 0.416, 0.584);
        insertOdds(GAME_ID, "moneyline", "home", -122, null);
        insertOdds(GAME_ID, "moneyline", "away", 102, null);
        insertOdds(GAME_ID, "run_line", "home", 169, -1.5);
        insertOdds(GAME_ID, "run_line", "away", -206, 1.5);
    }

    @Test
    void getGame_fullDetail() {
        seedGameWithOdds();
        UUID runId = insertSlateRun(LocalDate.of(2026, 4, 29), "v4", 1, 1);
        insertRecommendation(runId, GAME_ID, "moneyline", "away", "medium", null, CONTEXT);

        client.get()
                .uri("/game/" + GAME_ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.gameId")
                .isEqualTo(GAME_ID)
                .jsonPath("$.homeTeam")
                .isEqualTo("TBR")
                .jsonPath("$.awayTeam")
                .isEqualTo("CLE")
                .jsonPath("$.prediction.awayWinProb")
                .isEqualTo(0.584)
                .jsonPath("$.odds.moneyline.home.americanOdds")
                .isEqualTo(-122)
                .jsonPath("$.odds.moneyline.away.americanOdds")
                .isEqualTo(102)
                .jsonPath("$.odds.runLine.home.point")
                .isEqualTo(-1.5)
                .jsonPath("$.odds.runLine.away.point")
                .isEqualTo(1.5)
                .jsonPath("$.starters.home.name")
                .isEqualTo("Gavin Williams")
                .jsonPath("$.starters.away.name")
                .isEqualTo("Drew Rasmussen")
                .jsonPath("$.recommendation.side")
                .isEqualTo("away")
                .jsonPath("$.recommendation.decision")
                .isEqualTo("medium");
    }

    @Test
    void getGame_notFound() {
        client.get().uri("/game/bad-id").exchange().expectStatus().isNotFound();
    }

    @Test
    void getGame_noRecommendation() {
        seedGameWithOdds();

        client.get()
                .uri("/game/" + GAME_ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.gameId")
                .isEqualTo(GAME_ID)
                .jsonPath("$.prediction.homeWinProb")
                .isEqualTo(0.416)
                .jsonPath("$.recommendation")
                .doesNotExist()
                .jsonPath("$.starters")
                .doesNotExist();
    }
}
