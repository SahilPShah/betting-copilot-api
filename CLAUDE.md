---
description: 
alwaysApply: true
---

# Betting Copilot API — CLAUDE.md

> Authoritative implementation guide for the Spring Boot read-only REST API.
> Generated from `docs/api_design.md` + live database inspection on 2026-04-29.
> Where this file conflicts with `api_design.md`, **trust this file** — it reflects the actual DB.

---

## What this project is

A **read-only** Spring Boot 3 REST API over a PostgreSQL database populated by a Python scheduled job. No ML runs at request time. All predictions and recommendations are pre-computed and stored. The API is purely SQL queries wrapped in JSON responses.

---

## Technology Stack

| Layer | Choice |
|-------|--------|
| Framework | Spring Boot 3.x |
| ORM | Spring Data JPA + Hibernate |
| DB Driver | `org.postgresql:postgresql` |
| Build | Maven |
| Boilerplate | Lombok (`@Getter`, `@Setter`) |
| JSON | Jackson (included in Spring Boot) |

---

## Database Connection

**Host:** `betting-copilot-nyc1-do-user-36545166-0.f.db.ondigitalocean.com`
**Port:** `25060`
**Database:** `betting_copilot`
**User:** `doadmin`
**SSL:** required

Inject via environment variable — never hardcode credentials:

```
DATABASE_URL=postgresql://doadmin:PASSWORD@betting-copilot-nyc1-do-user-36545166-0.f.db.ondigitalocean.com:25060/betting_copilot?sslmode=require
```

---

## application.properties

```properties
spring.datasource.url=${DATABASE_URL}
spring.datasource.driver-class-name=org.postgresql.Driver

# CRITICAL: never let Hibernate touch the existing schema
spring.jpa.hibernate.ddl-auto=none

# CRITICAL: prevents Hibernate from camelCasing column names
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl

spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

server.port=8080
```

---

## Database Schema (verified against live DB)

### `slate_runs`

One row per pipeline run date. Groups all recommendations for that day.

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `slate_run_id` | UUID | NO | PK, auto-generated |
| `run_date` | DATE | NO | UNIQUE — one run per day |
| `model_version` | VARCHAR | NO | e.g. `v4_elo_logreg_l7` |
| `games_count` | INTEGER | YES | Total games analyzed |
| `picks_count` | INTEGER | YES | Final picks selected |
| `ran_at` | TIMESTAMPTZ | NO | When the Python job ran |

---

### `recommendations`

One row per (game × market × side) per run. The Python job may re-run on the same day — use `DISTINCT ON` + `ORDER BY created_at DESC` to deduplicate.

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `rec_id` | UUID | NO | PK |
| `slate_run_id` | UUID | YES | FK → slate_runs |
| `prediction_id` | UUID | YES | FK → predictions |
| `odds_snapshot_id` | UUID | YES | FK → odds_snapshots |
| `game_id` | VARCHAR | YES | FK → games |
| `market` | VARCHAR | NO | `moneyline` \| `run_line` |
| `side` | VARCHAR | NO | `home` \| `away` |
| `edge` | NUMERIC | NO | model_prob − implied_prob |
| `confidence` | NUMERIC | NO | 0–10 scale |
| `decision` | VARCHAR | NO | **See decision values below** |
| `no_bet_reason` | VARCHAR | YES | Nullable — why it was skipped |
| `context_snapshot` | JSONB | YES | See structure below |
| `llm_explanation` | TEXT | YES | Claude-generated narrative |
| `created_at` | TIMESTAMPTZ | NO | |

#### CRITICAL: Actual `decision` values

> **The design doc says `BET` / `PASS` — the live DB uses different values.**

| Value | Meaning | Use as |
|-------|---------|--------|
| `medium` | Selected pick — show in `picks` list | equivalent to "BET" |
| `no_bet` | Skipped — show in `noBets` list | equivalent to "PASS" |

When filtering for picks: `WHERE decision = 'medium'`
When filtering for skipped: `WHERE decision = 'no_bet'`

#### `no_bet_reason` values (observed in live DB)

`negative_edge` | `below_edge_threshold` | `not_top_pick` | `low_confidence` | `efficiently_priced` | NULL

#### `context_snapshot` JSONB structure

Fields present in current (v4) records:

```json
{
  "edge": 0.110,
  "bookmaker": "draftkings",
  "model_prob": 0.584,
  "implied_prob": 0.474,
  "american_odds": 102,
  "home_starter": {
    "name": "Gavin Williams",
    "era": 3.90,
    "l3_era": 4.50,
    "whip": 1.167
  },
  "away_starter": {
    "name": "Drew Rasmussen",
    "era": 3.21,
    "l3_era": 3.67,
    "whip": 0.922
  }
}
```

**Notes on JSONB parsing:**
- Older records (v3 and earlier) may not have `home_starter`/`away_starter` — these were absent from early slate runs
- Some early records have no starter data at all (`context_snapshot` may be NULL)
- Always use `.asText(null)` / `.asDouble()` with null-safe defaults; never assume fields exist
- ERA data lives **only** in `context_snapshot` — do not join `game_starters` for ERA

---

### `games`

One row per game.

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `game_id` | VARCHAR | NO | PK. Format: `YYYY-MM-DD-HOME-AWAY-N` e.g. `2026-04-29-ATH-KCR-1` |
| `game_date` | DATE | NO | |
| `first_pitch_utc` | TIMESTAMPTZ | YES | Often NULL — not always populated |
| `home_team_id` | VARCHAR | YES | 3-letter abbreviation e.g. `NYY` |
| `away_team_id` | VARCHAR | YES | |
| `status` | VARCHAR | YES | Default `scheduled`. Values: `scheduled` \| `final` |
| `home_score` | INTEGER | YES | NULL until final |
| `away_score` | INTEGER | YES | NULL until final |
| `created_at` | TIMESTAMPTZ | YES | |
| `data_source` | VARCHAR | YES | Default `live` |
| `sport` | VARCHAR | YES | Default `MLB` |
| `game_pk` | INTEGER | YES | MLB Stats API primary key |

---

### `predictions`

One row per game. Replaced on re-run (UNIQUE on `game_id`).

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `prediction_id` | UUID | NO | PK |
| `game_id` | VARCHAR | YES | UNIQUE FK → games |
| `model_version` | VARCHAR | NO | |
| `home_win_prob` | NUMERIC | NO | [0, 1] |
| `away_win_prob` | NUMERIC | NO | [0, 1] |
| `predicted_margin` | NUMERIC | YES | Positive = home wins by N runs |
| `predicted_total` | NUMERIC | YES | Predicted total runs scored |
| `home_cover_prob` | NUMERIC | YES | P(home covers −1.5) |
| `away_cover_prob` | NUMERIC | YES | P(away covers +1.5) |
| `elo_diff` | DOUBLE PRECISION | YES | home_elo − away_elo at prediction time |
| `created_at` | TIMESTAMPTZ | NO | |

---

### `odds_snapshots`

Multiple rows per game (different capture times, bookmakers).

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `snapshot_id` | UUID | NO | PK |
| `game_id` | VARCHAR | YES | FK → games |
| `bookmaker` | VARCHAR | NO | e.g. `draftkings` |
| `market` | VARCHAR | NO | `moneyline` \| `run_line` |
| `side` | VARCHAR | NO | `home` \| `away` |
| `american_odds` | INTEGER | NO | e.g. `-115`, `+145` |
| `run_line_point` | NUMERIC | YES | `-1.5` or `+1.5`, NULL for moneyline |
| `implied_prob` | NUMERIC | NO | Vig-removed probability |
| `captured_at` | TIMESTAMPTZ | NO | |
| `is_closing` | BOOLEAN | YES | Default false. True for closing line |

Latest odds query pattern:

```sql
SELECT DISTINCT ON (game_id, market, side) *
FROM odds_snapshots
WHERE game_id = :gameId
ORDER BY game_id, market, side, captured_at DESC
```

---

### `teams`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `team_id` | VARCHAR | NO | PK. 3-letter abbreviation e.g. `NYY` |
| `full_name` | VARCHAR | NO | e.g. `New York Yankees` |
| `division` | VARCHAR | YES | e.g. `AL East` |
| `sport` | VARCHAR | YES | Default `MLB` |
| `created_at` | TIMESTAMPTZ | YES | |

---

### Additional tables (not used by core API endpoints)

These tables exist in the DB and are populated by the Python job but are not queried by the four core endpoints. Do not join them unless building new endpoints.

| Table | Purpose |
|-------|---------|
| `game_starters` | Starter name + stats snapshot (ERA, WHIP, K/9, GS) per game/side. ERA for display comes from `context_snapshot`, not here. |
| `injury_statuses` | Player injury status per game, with `impact_score` |
| `pitcher_game_logs` | Per-start game logs (IP, ER, K, BB) per pitcher |
| `team_batting_logs` | Per-game team batting line (AB, H, HR, R, BB, K) |
| `team_season_stats` | Season-level team pitching + batting stats |
| `team_stats_mlb` | Last-7-day rolling team stats (win%, runs scored/allowed avg, bullpen ERA) |

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
    @Column(name = "predicted_total")   private Double predictedTotal;
    @Column(name = "home_cover_prob")   private Double homeCoverProb;
    @Column(name = "away_cover_prob")   private Double awayCoverProb;
    @Column(name = "elo_diff")          private Double eloDiff;
    @Column(name = "model_version")     private String modelVersion;
}
```

> **No `@GeneratedValue` on any entity** — the API is read-only. IDs are always set by the Python job.

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

### `GET /health`

DB liveness check.

**Response:**
```json
{
  "status": "ok",
  "latestGameDate": "2026-04-29",
  "latestSlateDate": "2026-04-29"
}
```

---

### `GET /slate` and `GET /slate/{date}`

`GET /slate` is equivalent to `GET /slate/{today}`. Date format: `YYYY-MM-DD`.

**Response:**
```json
{
  "runDate": "2026-04-29",
  "modelVersion": "v4_elo_logreg_l7",
  "gamesCount": 15,
  "picksCount": 5,
  "ranAt": "2026-04-29T13:00:37Z",
  "picks": [
    {
      "gameId": "2026-04-29-CLE-TBR-1",
      "homeTeam": "TBR",
      "awayTeam": "CLE",
      "market": "moneyline",
      "side": "away",
      "odds": 102,
      "edge": 0.110,
      "confidence": 7.3,
      "modelProb": 0.584,
      "impliedProb": 0.474,
      "decision": "medium",
      "homeStarter": "Gavin Williams",
      "homeStarterEra": 3.90,
      "homeStarterL3Era": 4.50,
      "awayStarter": "Drew Rasmussen",
      "awayStarterEra": 3.21,
      "awayStarterL3Era": 3.67,
      "llmExplanation": "..."
    }
  ],
  "noBets": [
    {
      "gameId": "2026-04-29-CHW-LAA-1",
      "reason": "negative_edge"
    }
  ]
}
```

**Implementation notes:**
- Fetch `SlateRun` by `run_date`. Return 404 if no run exists for that date.
- Fetch recommendations via `findDeduplicatedBySlateRunId()`.
- Split into `picks` (decision = `'medium'`) and `noBets` (decision = `'no_bet'`).
- `homeTeam`, `awayTeam`, `firstPitchUtc` — join to `games` table.
- Starter names and ERA come from `context_snapshot` JSONB, not from `game_starters`.
- Always guard JSONB field access — older records may lack starter fields.

---

### `GET /game/{gameId}`

Full detail for a single game. `gameId` format: `YYYY-MM-DD-HOME-AWAY-N`.

**Response:**
```json
{
  "gameId": "2026-04-29-CLE-TBR-1",
  "gameDate": "2026-04-29",
  "status": "scheduled",
  "homeTeam": "TBR",
  "awayTeam": "CLE",
  "firstPitchUtc": null,
  "prediction": {
    "homeWinProb": 0.416,
    "awayWinProb": 0.584,
    "predictedMargin": 1.2,
    "predictedTotal": 8.5,
    "homeCoverProb": 0.46,
    "awayCoverProb": 0.54,
    "eloDiff": -22.1
  },
  "odds": {
    "moneyline": {
      "home": { "americanOdds": -122, "impliedProb": 0.526 },
      "away": { "americanOdds": 102,  "impliedProb": 0.474 }
    },
    "runLine": {
      "home": { "point": -1.5, "americanOdds": 169 },
      "away": { "point":  1.5, "americanOdds": -206 }
    }
  },
  "starters": {
    "home": { "name": "Gavin Williams", "era": 3.90, "l3Era": 4.50, "whip": 1.167 },
    "away": { "name": "Drew Rasmussen", "era": 3.21, "l3Era": 3.67, "whip": 0.922 }
  },
  "recommendation": {
    "market": "moneyline",
    "side": "away",
    "edge": 0.110,
    "confidence": 7.3,
    "decision": "medium",
    "llmExplanation": "..."
  }
}
```

**Implementation notes:**
- 3 queries: `games`, `predictions` (by game_id), `odds_snapshots` (DISTINCT ON market+side).
- Starters come from `context_snapshot` of any recommendation for this game. Use the most recent one.
- `recommendation` field is nullable — not every game has a `medium` decision.
- `firstPitchUtc` is frequently NULL in the live DB; handle gracefully.

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
      "gameId": "2026-04-22-CLE-TBR-1",
      "runDate": "2026-04-22",
      "homeTeam": "TBR",
      "awayTeam": "CLE",
      "market": "moneyline",
      "side": "away",
      "odds": -136,
      "edge": 0.347,
      "confidence": 8.74,
      "decision": "medium",
      "outcome": "WIN",
      "homeScore": 2,
      "awayScore": 5
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
- Only return rows where `decision = 'medium'`.
- Join `recommendations` → `slate_runs` (for `run_date`) → `games` (for scores + status).
- Apply deduplication: `DISTINCT ON (game_id, market, side) ORDER BY created_at DESC` per run.
- `winRate` = wins / (wins + losses), excluding pending and pushes.

---

## JSONB Parsing Pattern

`context_snapshot` is stored as a raw JSON string in the entity. Parse it in the service layer with Jackson:

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

                JsonNode hs = ctx.path("home_starter");
                if (!hs.isMissingNode()) {
                    dto.setHomeStarter(hs.path("name").asText(null));
                    dto.setHomeStarterEra(hs.path("era").asDouble());
                    dto.setHomeStarterL3Era(hs.path("l3_era").asDouble());
                    dto.setHomeStarterWhip(hs.path("whip").asDouble());
                }

                JsonNode as = ctx.path("away_starter");
                if (!as.isMissingNode()) {
                    dto.setAwayStarter(as.path("name").asText(null));
                    dto.setAwayStarterEra(as.path("era").asDouble());
                    dto.setAwayStarterL3Era(as.path("l3_era").asDouble());
                    dto.setAwayStarterWhip(as.path("whip").asDouble());
                }
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
5. `GET /health` — end-to-end DB connectivity confirmed
6. `GET /slate` + `GET /slate/{date}` — core endpoint with JSONB parsing
7. `GET /game/{gameId}` — multi-table join
8. `GET /history` — outcome logic + pagination
9. `Dockerfile` — containerize for Droplet deployment

---

## Verification Commands

```bash
export DATABASE_URL="postgresql://doadmin:PASSWORD@betting-copilot-nyc1-do-user-36545166-0.f.db.ondigitalocean.com:25060/betting_copilot?sslmode=require"
mvn spring-boot:run

curl http://localhost:8080/health
curl http://localhost:8080/slate
curl http://localhost:8080/slate/2026-04-29
curl http://localhost:8080/game/2026-04-29-CLE-TBR-1
curl "http://localhost:8080/history?start_date=2026-04-01&end_date=2026-04-29"
```

---

## Known DB vs. Design Doc Discrepancies

| Topic | Design Doc Says | Live DB Reality |
|-------|----------------|-----------------|
| `decision` values | `BET` \| `PASS` | `medium` \| `no_bet` |
| `context_snapshot` starters | `name`, `era`, `l3_era` | `name`, `era`, `l3_era`, `whip` (whip added) |
| Older records | starter fields assumed present | `home_starter`/`away_starter` absent pre-v4; guard all JSONB access |
| `games` extra columns | not documented | `created_at`, `data_source`, `sport`, `game_pk` |
| `predictions` extra column | not documented | `predicted_total` NUMERIC |
| Extra tables | not documented | `game_starters`, `injury_statuses`, `pitcher_game_logs`, `team_batting_logs`, `team_season_stats`, `team_stats_mlb` |
