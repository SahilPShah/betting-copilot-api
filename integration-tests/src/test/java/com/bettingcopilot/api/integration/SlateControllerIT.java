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
class SlateControllerIT extends AbstractIntegrationTest {

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

    private LocalDate seedSlate() {
        LocalDate today = LocalDate.now();
        UUID runId = insertSlateRun(today, "v4", 2, 1);
        insertGame("g1", today, "TBR", "CLE", "scheduled", null, null);
        insertGame("g2", today, "LAA", "CHW", "scheduled", null, null);
        insertRecommendation(runId, "g1", "moneyline", "away", "medium", null, CONTEXT);
        insertRecommendation(runId, "g2", "moneyline", "home", "no_bet", "negative_edge", null);
        return today;
    }

    @Test
    void getSlate_byDate_returnsCorrectResponse() {
        LocalDate today = seedSlate();

        client.get()
                .uri("/slate/" + today)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.runDate")
                .isEqualTo(today.toString())
                .jsonPath("$.picks.length()")
                .isEqualTo(1)
                .jsonPath("$.picks[0].gameId")
                .isEqualTo("g1")
                .jsonPath("$.picks[0].homeTeam")
                .isEqualTo("TBR")
                .jsonPath("$.picks[0].odds")
                .isEqualTo(102)
                .jsonPath("$.picks[0].homeStarter")
                .isEqualTo("Gavin Williams")
                .jsonPath("$.noBets.length()")
                .isEqualTo(1)
                .jsonPath("$.noBets[0].gameId")
                .isEqualTo("g2")
                .jsonPath("$.noBets[0].reason")
                .isEqualTo("negative_edge");
    }

    @Test
    void getSlate_today_delegatesToCurrentDate() {
        seedSlate();

        client.get()
                .uri("/slate")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.picks.length()")
                .isEqualTo(1)
                .jsonPath("$.noBets.length()")
                .isEqualTo(1);
    }

    @Test
    void getSlate_unknownDate_returns404() {
        client.get().uri("/slate/1999-01-01").exchange().expectStatus().isNotFound();
    }
}
