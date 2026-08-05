# Plan: Shared Foundation

*Implement this first — all endpoint plans depend on it.*

---

## Package structure

```
com.bettingcopilot.api/
├── entity/
│   ├── SlateRun.java
│   ├── Recommendation.java
│   ├── Game.java
│   └── Prediction.java
├── repository/
│   ├── SlateRunRepository.java
│   ├── RecommendationRepository.java
│   ├── GameRepository.java
│   ├── PredictionRepository.java
│   └── OddsSnapshotRepository.java
├── service/          (one per endpoint — see individual plans)
├── controller/       (one per endpoint — see individual plans)
└── dto/              (see individual plans)
```

---

## Entities

Source of truth: `CLAUDE.md`. No `@GeneratedValue` on any `@Id` — IDs are set by the Python job.

```java
// entity/SlateRun.java
@Entity @Table(name = "slate_runs") @Getter @Setter
public class SlateRun {
    @Id @Column(name = "slate_run_id") private UUID slateRunId;
    @Column(name = "run_date")         private LocalDate runDate;
    @Column(name = "model_version")    private String modelVersion;
    @Column(name = "games_count")      private Integer gamesCount;
    @Column(name = "picks_count")      private Integer picksCount;
    @Column(name = "ran_at")           private OffsetDateTime ranAt;
}

// entity/Recommendation.java
@Entity @Table(name = "recommendations") @Getter @Setter
public class Recommendation {
    @Id @Column(name = "rec_id")       private UUID recId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slate_run_id") private SlateRun slateRun;
    @Column(name = "game_id")          private String gameId;
    @Column(name = "market")           private String market;
    @Column(name = "side")             private String side;
    @Column(name = "edge")             private Double edge;
    @Column(name = "confidence")       private Double confidence;
    @Column(name = "decision")         private String decision;
    @Column(name = "no_bet_reason")    private String noBetReason;
    @Column(name = "context_snapshot", columnDefinition = "jsonb")
                                       private String contextSnapshot;
    @Column(name = "llm_explanation")  private String llmExplanation;
    @Column(name = "created_at")       private OffsetDateTime createdAt;
}

// entity/Game.java
@Entity @Table(name = "games") @Getter @Setter
public class Game {
    @Id @Column(name = "game_id")       private String gameId;
    @Column(name = "game_date")         private LocalDate gameDate;
    @Column(name = "home_team_id")      private String homeTeamId;
    @Column(name = "away_team_id")      private String awayTeamId;
    @Column(name = "status")            private String status;
    @Column(name = "home_score")        private Integer homeScore;
    @Column(name = "away_score")        private Integer awayScore;
    @Column(name = "first_pitch_utc")   private OffsetDateTime firstPitchUtc;
}

// entity/Prediction.java
@Entity @Table(name = "predictions") @Getter @Setter
public class Prediction {
    @Id @Column(name = "prediction_id") private UUID predictionId;
    @Column(name = "game_id")           private String gameId;
    @Column(name = "home_win_prob")     private Double homeWinProb;
    @Column(name = "away_win_prob")     private Double awayWinProb;
    @Column(name = "predicted_margin")  private Double predictedMargin;
    @Column(name = "predicted_total")   private Double predictedTotal;
    @Column(name = "home_cover_prob")   private Double homeCoverProb;
    @Column(name = "away_cover_prob")   private Double awayCoverProb;
    @Column(name = "elo_diff")          private Double eloDiff;
    @Column(name = "model_version")     private String modelVersion;
}

// entity/OddsSnapshot.java
@Entity @Table(name = "odds_snapshots") @Getter @Setter
public class OddsSnapshot {
    @Id @Column(name = "snapshot_id")   private UUID snapshotId;
    @Column(name = "game_id")           private String gameId;
    @Column(name = "bookmaker")         private String bookmaker;
    @Column(name = "market")            private String market;
    @Column(name = "side")              private String side;
    @Column(name = "american_odds")     private Integer americanOdds;
    @Column(name = "run_line_point")    private Double runLinePoint;
    @Column(name = "implied_prob")      private Double impliedProb;
    @Column(name = "captured_at")       private OffsetDateTime capturedAt;
    @Column(name = "is_closing")        private Boolean isClosing;
}
```

---

## Repositories

```java
// SlateRunRepository.java
public interface SlateRunRepository extends JpaRepository<SlateRun, UUID> {
    Optional<SlateRun> findByRunDate(LocalDate runDate);
    Optional<SlateRun> findTopByOrderByRanAtDesc();
}

// RecommendationRepository.java
public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    // Returns latest rec per (game, market, side) — handles re-runs on the same day
    @Query(value = """
        SELECT DISTINCT ON (r.game_id, r.market, r.side) r.*
        FROM recommendations r
        WHERE r.slate_run_id = :slateRunId
        ORDER BY r.game_id, r.market, r.side, r.created_at DESC
        """, nativeQuery = true)
    List<Recommendation> findDeduplicatedBySlateRunId(@Param("slateRunId") UUID slateRunId);

    // Most recent recommendation for a given game (any decision)
    Optional<Recommendation> findTopByGameIdOrderByCreatedAtDesc(String gameId);

    // Most recent recommendation for a given game with a specific decision (e.g. "medium")
    Optional<Recommendation> findTopByGameIdAndDecisionOrderByCreatedAtDesc(String gameId, String decision);

    // For history: all BET recs in a date range via slate_run join
    @Query(value = """
        SELECT DISTINCT ON (r.game_id, r.market, r.side) r.*
        FROM recommendations r
        JOIN slate_runs sr ON r.slate_run_id = sr.slate_run_id
        WHERE r.decision = 'medium'
          AND sr.run_date BETWEEN :startDate AND :endDate
        ORDER BY r.game_id, r.market, r.side, r.created_at DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<Recommendation> findHistoryPicks(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query(value = """
        SELECT COUNT(*) FROM (
            SELECT DISTINCT ON (r.game_id, r.market, r.side) r.rec_id
            FROM recommendations r
            JOIN slate_runs sr ON r.slate_run_id = sr.slate_run_id
            WHERE r.decision = 'medium'
              AND sr.run_date BETWEEN :startDate AND :endDate
            ORDER BY r.game_id, r.market, r.side, r.created_at DESC
        ) sub
        """, nativeQuery = true)
    long countHistoryPicks(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}

// GameRepository.java
public interface GameRepository extends JpaRepository<Game, String> {
    List<Game> findByGameDate(LocalDate date);
    Optional<Game> findTopByOrderByGameDateDesc();
}

// PredictionRepository.java
public interface PredictionRepository extends JpaRepository<Prediction, UUID> {
    Optional<Prediction> findByGameId(String gameId);
}

// OddsSnapshotRepository.java
public interface OddsSnapshotRepository extends JpaRepository<OddsSnapshot, UUID> {

    @Query(value = """
        SELECT DISTINCT ON (market, side) *
        FROM odds_snapshots
        WHERE game_id = :gameId
        ORDER BY market, side, captured_at DESC
        """, nativeQuery = true)
    List<OddsSnapshot> findLatestByGameId(@Param("gameId") String gameId);
}
```

---

## Spring Actuator (replaces custom /health)

### pom.xml addition
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### application.properties additions
```properties
management.endpoints.web.exposure.include=health,metrics,info
management.endpoint.health.show-details=always
```

### Endpoints provided (no code required)
- `GET /actuator/health` — liveness + DB connectivity (DataSource indicator auto-configured)
- `GET /actuator/metrics` — JVM, HTTP request, datasource pool metrics

---

## Test infrastructure

### pom.xml additions

**Dependencies (test scope):**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

**Testcontainers BOM in `<dependencyManagement>`:**
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>1.20.4</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

### Integration test base class

`src/test/java/com/bettingcopilot/api/AbstractIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("betting_copilot_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Spring expects a JDBC URL for spring.datasource.url
        registry.add("DATABASE_URL", () -> postgres.getJdbcUrl() + "?sslmode=disable");
    }
}
```

### Test schema

`src/test/resources/schema.sql` — DDL for the 5 tables used by tests:

```sql
CREATE TABLE IF NOT EXISTS slate_runs (
    slate_run_id UUID PRIMARY KEY,
    run_date     DATE NOT NULL UNIQUE,
    model_version VARCHAR NOT NULL,
    games_count  INTEGER,
    picks_count  INTEGER,
    ran_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS games (
    game_id       VARCHAR PRIMARY KEY,
    game_date     DATE NOT NULL,
    first_pitch_utc TIMESTAMPTZ,
    home_team_id  VARCHAR,
    away_team_id  VARCHAR,
    status        VARCHAR DEFAULT 'scheduled',
    home_score    INTEGER,
    away_score    INTEGER
);

CREATE TABLE IF NOT EXISTS predictions (
    prediction_id  UUID PRIMARY KEY,
    game_id        VARCHAR UNIQUE,
    model_version  VARCHAR NOT NULL,
    home_win_prob  NUMERIC NOT NULL,
    away_win_prob  NUMERIC NOT NULL,
    predicted_margin NUMERIC,
    predicted_total  NUMERIC,
    home_cover_prob  NUMERIC,
    away_cover_prob  NUMERIC,
    elo_diff         DOUBLE PRECISION,
    created_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS odds_snapshots (
    snapshot_id    UUID PRIMARY KEY,
    game_id        VARCHAR,
    bookmaker      VARCHAR NOT NULL,
    market         VARCHAR NOT NULL,
    side           VARCHAR NOT NULL,
    american_odds  INTEGER NOT NULL,
    run_line_point NUMERIC,
    implied_prob   NUMERIC NOT NULL,
    captured_at    TIMESTAMPTZ NOT NULL,
    is_closing     BOOLEAN DEFAULT false
);

CREATE TABLE IF NOT EXISTS recommendations (
    rec_id           UUID PRIMARY KEY,
    slate_run_id     UUID REFERENCES slate_runs(slate_run_id),
    prediction_id    UUID,
    odds_snapshot_id UUID,
    game_id          VARCHAR,
    market           VARCHAR NOT NULL,
    side             VARCHAR NOT NULL,
    edge             NUMERIC NOT NULL,
    confidence       NUMERIC NOT NULL,
    decision         VARCHAR NOT NULL,
    no_bet_reason    VARCHAR,
    context_snapshot JSONB,
    llm_explanation  TEXT,
    created_at       TIMESTAMPTZ NOT NULL
);
```

### Test application properties

`src/test/resources/application-test.properties`:
```properties
spring.sql.init.mode=always
spring.jpa.hibernate.ddl-auto=none
```

---

## Verification

1. Add entities, repositories, and Actuator config.
2. Start app with `DATABASE_URL` set: `GET /actuator/health` → `{"status":"UP","components":{"db":{"status":"UP",...}}}`
3. Run unit tests: `mvn test` — all pass without Docker.
4. Run integration tests: `mvn verify` — Testcontainers spins up PostgreSQL, schema applied, all pass.
