package com.bettingcopilot.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // Singleton container shared by every IT class: the Spring context is cached across
    // classes, so the container must outlive any single class (unlike @Container semantics).
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("betting_copilot_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Spring expects a JDBC URL for spring.datasource.url; credentials are separate properties
        registry.add("DATABASE_URL", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired protected JdbcTemplate jdbcTemplate;

    protected void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM recommendations");
        jdbcTemplate.update("DELETE FROM odds_snapshots");
        jdbcTemplate.update("DELETE FROM predictions");
        jdbcTemplate.update("DELETE FROM slate_runs");
        jdbcTemplate.update("DELETE FROM games");
    }

    protected UUID insertSlateRun(
            LocalDate runDate, String modelVersion, Integer gamesCount, Integer picksCount) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
            INSERT INTO slate_runs (slate_run_id, run_date, model_version, games_count, picks_count, ran_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
                id,
                runDate,
                modelVersion,
                gamesCount,
                picksCount,
                OffsetDateTime.now());
        return id;
    }

    protected void insertGame(
            String gameId,
            LocalDate gameDate,
            String homeTeam,
            String awayTeam,
            String status,
            Integer homeScore,
            Integer awayScore) {
        jdbcTemplate.update(
                """
            INSERT INTO games (game_id, game_date, home_team_id, away_team_id, status, home_score, away_score)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
                gameId,
                gameDate,
                homeTeam,
                awayTeam,
                status,
                homeScore,
                awayScore);
    }

    protected void insertPrediction(String gameId, double homeWinProb, double awayWinProb) {
        jdbcTemplate.update(
                """
            INSERT INTO predictions (prediction_id, game_id, model_version, home_win_prob, away_win_prob,
                                     predicted_margin, predicted_total, home_cover_prob, away_cover_prob,
                                     elo_diff, created_at)
            VALUES (?, ?, 'v4-test', ?, ?, 1.2, 8.5, 0.46, 0.54, -22.1, ?)
            """,
                UUID.randomUUID(),
                gameId,
                homeWinProb,
                awayWinProb,
                OffsetDateTime.now());
    }

    protected void insertOdds(
            String gameId, String market, String side, int americanOdds, Double runLinePoint) {
        jdbcTemplate.update(
                """
            INSERT INTO odds_snapshots (snapshot_id, game_id, bookmaker, market, side, american_odds,
                                        run_line_point, implied_prob, captured_at)
            VALUES (?, ?, 'draftkings', ?, ?, ?, ?, 0.5, ?)
            """,
                UUID.randomUUID(),
                gameId,
                market,
                side,
                americanOdds,
                runLinePoint,
                OffsetDateTime.now());
    }

    protected void insertRecommendation(
            UUID slateRunId,
            String gameId,
            String market,
            String side,
            String decision,
            String noBetReason,
            String contextSnapshot) {
        jdbcTemplate.update(
                """
            INSERT INTO recommendations (rec_id, slate_run_id, game_id, market, side, edge, confidence,
                                         decision, no_bet_reason, context_snapshot, llm_explanation, created_at)
            VALUES (?, ?, ?, ?, ?, 0.11, 7.3, ?, ?, ?::jsonb, 'test explanation', ?)
            """,
                UUID.randomUUID(),
                slateRunId,
                gameId,
                market,
                side,
                decision,
                noBetReason,
                contextSnapshot,
                OffsetDateTime.now());
    }
}
