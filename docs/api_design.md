# Betting Copilot — API Design Reference

*For use when implementing the Spring Boot API in a separate repository.*
*Last updated: 2026-04-29*

---

## Overview

The API is a **read-only** REST service over a PostgreSQL database populated by a Python scheduled job. No ML runs at request time. All predictions and recommendations are pre-computed and stored — the API is purely SQL queries.

**Base URL (local):** `http://localhost:8080`
**Base URL (production):** `http://206.189.224.55:8080` *(Droplet IP, port TBD)*

---

## Database Connection

**Host:** `betting-copilot-nyc1-do-user-36545166-0.f.db.ondigitalocean.com`
**Port:** `25060`
**Database:** `betting_copilot`
**User:** `doadmin`
**SSL:** required

Inject via environment variable — never hardcode:
```
DATABASE_URL=jdbc:postgresql://doadmin:PASSWORD@host:25060/betting_copilot?sslmode=require
```

---

## Technology Stack

| Layer | Choice |
|-------|--------|
| Framework | Spring Boot 4.0.6 |
| ORM | Spring Data JPA + Hibernate |
| DB Driver | `org.postgresql:postgresql` |
| Build | Maven |
| Boilerplate | Lombok (`@Getter`, `@Setter`) |
| JSON | Jackson (included in Spring Boot) |

---

## JPA Configuration (application.properties)

```properties
spring.datasource.url=${DATABASE_URL}
spring.datasource.driver-class-name=org.postgresql.Driver

# CRITICAL: never let Hibernate touch the existing schema
spring.jpa.hibernate.ddl-auto=none

# CRITICAL: prevents Hibernate from camelCasing your column names
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl

spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

server.port=8080
```

---

## Database Schema

### `slate_runs`
One row per pipeline run date. Groups all recommendations for that day.

| Column | Type | Notes |
|--------|------|-------|
| `slate_run_id` | UUID PK | |
| `run_date` | DATE | UNIQUE — one run per day |
| `model_version` | VARCHAR | e.g. `v4_elo_logreg_l7` |
| `games_count` | INTEGER | Total games analyzed |
| `picks_count` | INTEGER | Final picks selected (≤ 5) |
| `ran_at` | TIMESTAMPTZ | When the job ran |

### `recommendations`
One row per (game × market × side) per run. Contains rich JSONB context.

| Column | Type | Notes |
|--------|------|-------|
| `rec_id` | UUID PK | |
| `slate_run_id` | UUID FK → slate_runs | |
| `prediction_id` | UUID FK → predictions | |
| `odds_snapshot_id` | UUID FK → odds_snapshots | |
| `game_id` | VARCHAR FK → games | |
| `market` | VARCHAR | `moneyline` \| `run_line` |
| `side` | VARCHAR | `home` \| `away` |
| `edge` | NUMERIC | model_prob − implied_prob |
| `confidence` | NUMERIC | 0–10 scale |
| `decision` | VARCHAR | `medium` \| `no_bet` |
| `no_bet_reason` | VARCHAR | Nullable — why it was skipped |
| `context_snapshot` | JSONB | See structure below |
| `llm_explanation` | TEXT | Claude-generated narrative, nullable |
| `created_at` | TIMESTAMPTZ | |

#### `context_snapshot` JSONB structure
```json
{
  "model_prob": 0.582,
  "implied_prob": 0.516,
  "edge": 0.066,
  "bookmaker": "draftkings",
  "american_odds": -115,
  "home_starter": {
    "name": "Severino",
    "era": 2.89,
    "l3_era": 2.75,
    "whip": 1.05
  },
  "away_starter": {
    "name": "Bibee",
    "era": 3.45,
    "l3_era": 3.12,
    "whip": 1.10
  }
}
```

> Starter ERA data lives **only** in `context_snapshot` — do not join `game_starters` for ERA. That table stores names only; ERA is computed dynamically by the Python job and stored here.

### `games`
One row per game.

| Column | Type | Notes |
|--------|------|-------|
| `game_id` | VARCHAR PK | Format: `YYYY-MM-DD-HOME-AWAY-N` e.g. `2026-04-28-NYY-BOS-1` |
| `game_date` | DATE | |
| `first_pitch_utc` | TIMESTAMPTZ | Nullable |
| `home_team_id` | VARCHAR | 3-letter abbreviation e.g. `NYY` |
| `away_team_id` | VARCHAR | |
| `status` | VARCHAR | `scheduled` \| `final` |
| `home_score` | INTEGER | Nullable until final |
| `away_score` | INTEGER | Nullable until final |

### `predictions`
One row per game. Replaced on re-run (UNIQUE on game_id).

| Column | Type | Notes |
|--------|------|-------|
| `prediction_id` | UUID PK | |
| `game_id` | VARCHAR UNIQUE FK | |
| `model_version` | VARCHAR | |
| `home_win_prob` | NUMERIC | [0, 1] |
| `away_win_prob` | NUMERIC | [0, 1] |
| `predicted_margin` | NUMERIC | Positive = home wins by N runs |
| `home_cover_prob` | NUMERIC | P(home covers −1.5) |
| `away_cover_prob` | NUMERIC | P(away covers +1.5) |
| `elo_diff` | FLOAT | home_elo − away_elo at prediction time |
| `created_at` | TIMESTAMPTZ | |

### `odds_snapshots`
Multiple rows per game (different captures, bookmakers).

| Column | Type | Notes |
|--------|------|-------|
| `snapshot_id` | UUID PK | |
| `game_id` | VARCHAR FK | |
| `bookmaker` | VARCHAR | e.g. `draftkings` |
| `market` | VARCHAR | `moneyline` \| `run_line` |
| `side` | VARCHAR | `home` \| `away` |
| `american_odds` | INTEGER | e.g. `−115`, `+145` |
| `run_line_point` | NUMERIC | `−1.5` or `+1.5`, NULL for moneyline |
| `implied_prob` | NUMERIC | Vig-removed probability |
| `captured_at` | TIMESTAMPTZ | |
| `is_closing` | BOOLEAN | True for closing line |

Latest odds query pattern:
```sql
SELECT DISTINCT ON (game_id, market, side) *
FROM odds_snapshots
WHERE game_id = :gameId
ORDER BY game_id, market, side, captured_at DESC
```

---

## JPA Entity Mappings

```java
// SlateRun.java
@Entity @Table(name = "slate_runs") @Getter @Setter
public class SlateRun {
    @Id @Column(name = "slate_run_id") private UUID slateRunId;
    @Column(name = "run_date")         private LocalDate runDate;
    @Column(name = "model_version")    private String modelVersion;
    @Column(name = "games_count")      private Integer gamesCount;
    @Column(name = "picks_count")      private Integer picksCount;
    @Column(name = "ran_at")           private OffsetDateTime ranAt;
}

// Recommendation.java
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

// Game.java
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

// Prediction.java
@Entity @Table(name = "predictions") @Getter @Setter
public class Prediction {
    @Id @Column(name = "prediction_id") private UUID predictionId;
    @Column(name = "game_id")           private String gameId;
    @Column(name = "home_win_prob")     private Double homeWinProb;
    @Column(name = "away_win_prob")     private Double awayWinProb;
    @Column(name = "predicted_margin")  private Double predictedMargin;
    @Column(name = "home_cover_prob")   private Double homeCoverProb;
    @Column(name = "away_cover_prob")   private Double awayCoverProb;
    @Column(name = "elo_diff")          private Double eloDiff;
    @Column(name = "model_version")     private String modelVersion;
}
```

> **No `@GeneratedValue` on any entity** — the API is read-only, IDs are always set by the Python job.

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

    // Deduplicates if the job ran twice in a day — returns latest per (game, market, side)
    @Query(value = """
        SELECT DISTINCT ON (r.game_id, r.market, r.side) r.*
        FROM recommendations r
        WHERE r.slate_run_id = :slateRunId
        ORDER BY r.game_id, r.market, r.side, r.created_at DESC
        """, nativeQuery = true)
    List<Recommendation> findDeduplicatedBySlateRunId(@Param("slateRunId") UUID slateRunId);
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
```

---

## Endpoints

### `GET /actuator/health`
DB liveness check via Spring Actuator. Includes datasource connectivity when a `DataSource` is configured.

---

### `GET /slate`
Today's picks. Equivalent to `GET /slate/{today}`.

### `GET /slate/{date}`
Picks for a specific date. `date` format: `YYYY-MM-DD`.

**Response:**
```json
{
  "runDate": "2026-04-29",
  "modelVersion": "v4_elo_logreg_l7",
  "gamesCount": 15,
  "picksCount": 3,
  "ranAt": "2026-04-29T13:00:37Z",
  "picks": [
    {
      "gameId": "2026-04-29-NYY-BOS-1",
      "homeTeam": "NYY",
      "awayTeam": "BOS",
      "market": "moneyline",
      "side": "home",
      "odds": -115,
      "edge": 0.067,
      "confidence": 7.3,
      "modelProb": 0.582,
      "impliedProb": 0.515,
      "decision": "medium",
      "homeStarter": "Severino",
      "homeStarterL3Era": 2.75,
      "awayStarter": "Bibee",
      "awayStarterL3Era": 3.12,
      "llmExplanation": "NYY holds a significant edge on starting pitching..."
    }
  ],
  "noBets": [
    {
      "gameId": "2026-04-29-LAD-SFG-1",
      "reason": "edge below threshold"
    }
  ]
}
```

**Implementation notes:**
- Fetch `SlateRun` by `run_date`. Return 404 if no run exists for that date.
- Fetch recommendations via `findDeduplicatedBySlateRunId()`.
- Split into `picks` (decision = `medium`) and `noBets` (decision = `no_bet`).
- `homeTeam`, `awayTeam`, `firstPitchUtc` — join to `games` table.
- Starter names + ERA come from `context_snapshot` JSONB — no join to `game_starters`.

---

### `GET /game/{gameId}`
Full detail for a single game.

**Path param:** `gameId` e.g. `2026-04-29-NYY-BOS-1`

**Response:**
```json
{
  "gameId": "2026-04-29-NYY-BOS-1",
  "gameDate": "2026-04-29",
  "status": "scheduled",
  "homeTeam": "NYY",
  "awayTeam": "BOS",
  "firstPitchUtc": "2026-04-29T23:10:00Z",
  "prediction": {
    "homeWinProb": 0.582,
    "awayWinProb": 0.418,
    "predictedMargin": 1.2,
    "homeCoverProb": 0.54,
    "awayCoverProb": 0.46,
    "eloDiff": 45.2
  },
  "odds": {
    "moneyline": {
      "home": { "americanOdds": -115, "impliedProb": 0.535 },
      "away": { "americanOdds": 105,  "impliedProb": 0.465 }
    },
    "runLine": {
      "home": { "point": -1.5, "americanOdds": -110 },
      "away": { "point":  1.5, "americanOdds": -110 }
    }
  },
  "starters": {
    "home": { "name": "Severino", "era": 2.89, "l3Era": 2.75 },
    "away": { "name": "Bibee",    "era": 3.45, "l3Era": 3.12 }
  },
  "recommendation": {
    "market": "moneyline",
    "side": "home",
    "edge": 0.047,
    "confidence": 7.3,
    "decision": "medium",
    "llmExplanation": "..."
  }
}
```

**Implementation notes:**
- 3 queries: `games`, `predictions` (by game_id), `odds_snapshots` (DISTINCT ON market+side)
- Starters come from `context_snapshot` of the recommendation if one exists for this game.
- `recommendation` field is nullable — not every game has a `medium` decision.

---

### `GET /history`
Past picks with WIN/LOSS outcomes computed from final scores.

**Query params:**

| Param | Default | Notes |
|-------|---------|-------|
| `start_date` | 30 days ago | `YYYY-MM-DD` |
| `end_date` | today | `YYYY-MM-DD` |
| `page` | 1 | Pagination |
| `per_page` | 20 | Max results per page |

**Response:**
```json
{
  "summary": {
    "wins": 15,
    "losses": 8,
    "pushes": 0,
    "pending": 2,
    "winRate": 0.652
  },
  "picks": [
    {
      "gameId": "2026-04-22-NYY-BOS-1",
      "runDate": "2026-04-22",
      "homeTeam": "NYY",
      "awayTeam": "BOS",
      "market": "moneyline",
      "side": "home",
      "odds": -115,
      "edge": 0.045,
      "confidence": 7.1,
      "decision": "medium",
      "outcome": "WIN",
      "homeScore": 4,
      "awayScore": 2
    }
  ],
  "pagination": {
    "page": 1,
    "perPage": 20,
    "total": 25
  }
}
```

**Outcome logic:**

```java
// Moneyline
boolean homeWon = homeScore > awayScore;
outcome = (side.equals("home") == homeWon) ? "WIN" : "LOSS";

// Run line (spread always ±1.5)
double margin = homeScore - awayScore; // positive = home won by margin
boolean homeCovered = margin > 1.5;
outcome = (side.equals("home") == homeCovered) ? "WIN" : "LOSS";

// Not yet final
if (!"final".equals(status)) outcome = "PENDING";
```

**Implementation notes:**
- Query `recommendations` joined to `slate_runs` (for `run_date`) and `games` (for scores + status).
- Only return rows where `decision = 'medium'`.
- Apply deduplication: `DISTINCT ON (game_id, market, side) ORDER BY created_at DESC` per run.
- `winRate` = wins / (wins + losses), excluding pending and pushes.

---

## JSONB Parsing Pattern

`context_snapshot` is stored as a JSON string in the entity. Parse it in the service layer with Jackson:

```java
@Service
public class SlateService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PickDto toPickDto(Recommendation rec) {
        PickDto dto = new PickDto();
        // ... map scalar fields ...

        if (rec.getContextSnapshot() != null) {
            try {
                JsonNode ctx = objectMapper.readTree(rec.getContextSnapshot());
                dto.setModelProb(ctx.path("model_prob").asDouble());
                dto.setImpliedProb(ctx.path("implied_prob").asDouble());
                dto.setOdds(ctx.path("american_odds").asInt());
                dto.setHomeStarter(ctx.path("home_starter").path("name").asText(null));
                dto.setHomeStarterL3Era(ctx.path("home_starter").path("l3_era").asDouble());
                dto.setAwayStarter(ctx.path("away_starter").path("name").asText(null));
                dto.setAwayStarterL3Era(ctx.path("away_starter").path("l3_era").asDouble());
            } catch (JsonProcessingException e) {
                // older records may have a different shape — leave fields null
            }
        }
        return dto;
    }
}
```

---

## Implementation Order

Build in this sequence — each step is independently testable:

1. `pom.xml` + `BettingCopilotApplication.java` — project compiles
2. `application.properties` — DB connection via `DATABASE_URL` env var
3. All 4 entities — confirm Hibernate maps without errors on startup
4. All 4 repositories — confirm Spring Data queries work
5. `GET /actuator/health` — end-to-end DB connectivity confirmed
6. `GET /slate` + `GET /slate/{date}` — core endpoint with JSONB parsing
7. `GET /game/{gameId}` — multi-table join
8. `GET /history` — outcome logic + pagination
9. `Dockerfile` — containerize for Droplet deployment

---

## Verification

```bash
# Run locally with DO DB
export DATABASE_URL="jdbc:postgresql://doadmin:PASSWORD@betting-copilot-nyc1-do-user-36545166-0.f.db.ondigitalocean.com:25060/betting_copilot?sslmode=require"
mvn spring-boot:run

curl http://localhost:8080/actuator/health
curl http://localhost:8080/slate
curl http://localhost:8080/slate/2026-04-29
curl http://localhost:8080/game/2026-04-29-NYY-BOS-1
curl "http://localhost:8080/history?start_date=2026-04-01&end_date=2026-04-29"
```
