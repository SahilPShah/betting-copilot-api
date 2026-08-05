package com.bettingcopilot.api.integration;

import com.bettingcopilot.api.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@Tag("integration")
class HealthControllerIT extends AbstractIntegrationTest {

    @Autowired RestTestClient client;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @Test
    void getHealth_returnsLatestDates() {
        insertGame(
                "2026-04-29-CLE-TBR-1",
                LocalDate.of(2026, 4, 29),
                "TBR",
                "CLE",
                "scheduled",
                null,
                null);
        insertSlateRun(LocalDate.of(2026, 4, 28), "v4", 15, 5);

        client.get()
                .uri("/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("ok")
                .jsonPath("$.latestGameDate")
                .isEqualTo("2026-04-29")
                .jsonPath("$.latestSlateDate")
                .isEqualTo("2026-04-28");
    }

    @Test
    void getHealth_emptyDatabase_returnsOkWithNullDates() {
        client.get()
                .uri("/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("ok")
                .jsonPath("$.latestGameDate")
                .doesNotExist()
                .jsonPath("$.latestSlateDate")
                .doesNotExist();
    }
}
