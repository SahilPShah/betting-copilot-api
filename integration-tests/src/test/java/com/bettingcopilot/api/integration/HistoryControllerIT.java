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
class HistoryControllerIT extends AbstractIntegrationTest {

    private static final LocalDate DAY_ONE = LocalDate.of(2026, 4, 20);
    private static final LocalDate DAY_TWO = LocalDate.of(2026, 4, 21);

    private static final String CONTEXT =
            """
        {"american_odds": -136}
        """;

    @Autowired RestTestClient client;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        UUID runOne = insertSlateRun(DAY_ONE, "v4", 2, 2);
        UUID runTwo = insertSlateRun(DAY_TWO, "v4", 3, 1);

        // Two final games with scores, one still scheduled
        insertGame("g1", DAY_ONE, "TBR", "CLE", "final", 2, 5);
        insertGame("g2", DAY_ONE, "NYY", "BOS", "final", 6, 1);
        insertGame("g3", DAY_TWO, "LAD", "SFG", "scheduled", null, null);

        // Three medium picks + two no_bets
        insertRecommendation(
                runOne, "g1", "moneyline", "away", "medium", null, CONTEXT); // away won -> WIN
        insertRecommendation(
                runOne, "g2", "moneyline", "away", "medium", null, CONTEXT); // home won -> LOSS
        insertRecommendation(
                runTwo, "g3", "moneyline", "home", "medium", null, CONTEXT); // scheduled -> PENDING
        insertRecommendation(runOne, "g2", "run_line", "home", "no_bet", "negative_edge", null);
        insertRecommendation(runTwo, "g3", "run_line", "away", "no_bet", "low_confidence", null);
    }

    private String uri(String query) {
        return "/history?start_date=" + DAY_ONE + "&end_date=" + DAY_TWO + query;
    }

    @Test
    void getHistory_onlyMediumDecisionsReturned() {
        client.get()
                .uri(uri(""))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.picks.length()")
                .isEqualTo(3)
                .jsonPath("$.pagination.total")
                .isEqualTo(3);
    }

    @Test
    void getHistory_correctOutcomeCounts() {
        client.get()
                .uri(uri(""))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.summary.wins")
                .isEqualTo(1)
                .jsonPath("$.summary.losses")
                .isEqualTo(1)
                .jsonPath("$.summary.pending")
                .isEqualTo(1)
                .jsonPath("$.summary.pushes")
                .isEqualTo(0)
                .jsonPath("$.summary.winRate")
                .isEqualTo(0.5)
                .jsonPath("$.picks[?(@.gameId == 'g1')].outcome")
                .isEqualTo("WIN")
                .jsonPath("$.picks[?(@.gameId == 'g1')].odds")
                .isEqualTo(-136)
                .jsonPath("$.picks[?(@.gameId == 'g2')].outcome")
                .isEqualTo("LOSS")
                .jsonPath("$.picks[?(@.gameId == 'g3')].outcome")
                .isEqualTo("PENDING");
    }

    @Test
    void getHistory_dateRangeFilter() {
        client.get()
                .uri("/history?start_date=" + DAY_ONE + "&end_date=" + DAY_ONE)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.picks.length()")
                .isEqualTo(2)
                .jsonPath("$.summary.pending")
                .isEqualTo(0);
    }

    @Test
    void getHistory_pagination() {
        client.get()
                .uri(uri("&page=2&per_page=2"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.picks.length()")
                .isEqualTo(1)
                .jsonPath("$.pagination.page")
                .isEqualTo(2)
                .jsonPath("$.pagination.perPage")
                .isEqualTo(2)
                .jsonPath("$.pagination.total")
                .isEqualTo(3)
                // Summary still spans the whole range
                .jsonPath("$.summary.wins")
                .isEqualTo(1);
    }
}
