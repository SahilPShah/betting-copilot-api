# Plan: GET /history

*Prerequisite: `plan_shared.md` (entities, repos, test infra)*

---

## Endpoint

| Method | Path | Description |
|--------|------|-------------|
| GET | `/history` | Past picks with WIN/LOSS/PENDING outcomes |

### Query parameters

| Param | Default | Format |
|-------|---------|--------|
| `start_date` | 30 days ago | `YYYY-MM-DD` |
| `end_date` | today | `YYYY-MM-DD` |
| `page` | `1` | integer |
| `per_page` | `20` | integer |

---

## Files to create

```
dto/
├── HistoryPickDto.java
├── HistorySummaryDto.java
├── PaginationDto.java
└── HistoryResponse.java
service/
└── HistoryService.java
controller/
└── HistoryController.java
```

---

## DTOs

```java
// dto/HistoryPickDto.java
@Getter @Setter
public class HistoryPickDto {
    private String gameId;
    private LocalDate runDate;
    private String homeTeam;
    private String awayTeam;
    private String market;
    private String side;
    private Integer odds;
    private Double edge;
    private Double confidence;
    private String decision;
    private String outcome;      // "WIN" | "LOSS" | "PENDING"
    private Integer homeScore;
    private Integer awayScore;
}

// dto/HistorySummaryDto.java
@Getter @Setter
public class HistorySummaryDto {
    private int wins;
    private int losses;
    private int pushes;
    private int pending;
    private Double winRate;      // wins / (wins + losses); null if no resolved picks
}

// dto/PaginationDto.java
@Getter @Setter
public class PaginationDto {
    private int page;
    private int perPage;
    private long total;
}

// dto/HistoryResponse.java
@Getter @Setter
public class HistoryResponse {
    private HistorySummaryDto summary;
    private List<HistoryPickDto> picks;
    private PaginationDto pagination;
}
```

---

## Service

```java
// service/HistoryService.java
@Service
public class HistoryService {

    private final RecommendationRepository recRepo;
    private final GameRepository gameRepo;
    private final SlateRunRepository slateRunRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HistoryResponse getHistory(
            LocalDate startDate, LocalDate endDate, int page, int perPage) {

        int offset = (page - 1) * perPage;

        List<Recommendation> recs = recRepo.findHistoryPicks(startDate, endDate, perPage, offset);
        long total = recRepo.countHistoryPicks(startDate, endDate);

        // Batch-fetch games for all rec game IDs
        Set<String> gameIds = recs.stream().map(Recommendation::getGameId).collect(toSet());
        Map<String, Game> gameMap = gameRepo.findAllById(gameIds)
            .stream().collect(toMap(Game::getGameId, g -> g));

        // Batch-fetch slate runs for run_date
        Map<UUID, SlateRun> runMap = new HashMap<>();
        recs.stream().map(r -> r.getSlateRun().getSlateRunId()).distinct()
            .forEach(id -> slateRunRepo.findById(id).ifPresent(sr -> runMap.put(id, sr)));

        List<HistoryPickDto> picks = new ArrayList<>();
        int wins = 0, losses = 0, pushes = 0, pending = 0;

        for (Recommendation rec : recs) {
            Game game = gameMap.get(rec.getGameId());
            SlateRun run = runMap.get(rec.getSlateRun().getSlateRunId());

            HistoryPickDto dto = new HistoryPickDto();
            dto.setGameId(rec.getGameId());
            dto.setRunDate(run != null ? run.getRunDate() : null);
            dto.setMarket(rec.getMarket());
            dto.setSide(rec.getSide());
            dto.setEdge(rec.getEdge());
            dto.setConfidence(rec.getConfidence());
            dto.setDecision(rec.getDecision());

            // Odds from context_snapshot
            if (rec.getContextSnapshot() != null) {
                try {
                    JsonNode ctx = objectMapper.readTree(rec.getContextSnapshot());
                    dto.setOdds(ctx.path("american_odds").asInt());
                } catch (JsonProcessingException ignored) {}
            }

            if (game != null) {
                dto.setHomeTeam(game.getHomeTeamId());
                dto.setAwayTeam(game.getAwayTeamId());
                dto.setHomeScore(game.getHomeScore());
                dto.setAwayScore(game.getAwayScore());

                String outcome = computeOutcome(rec.getMarket(), rec.getSide(),
                    game.getHomeScore(), game.getAwayScore(), game.getStatus());
                dto.setOutcome(outcome);

                switch (outcome) {
                    case "WIN"     -> wins++;
                    case "LOSS"    -> losses++;
                    case "PENDING" -> pending++;
                    default        -> pushes++;
                }
            }

            picks.add(dto);
        }

        HistorySummaryDto summary = new HistorySummaryDto();
        summary.setWins(wins);
        summary.setLosses(losses);
        summary.setPushes(pushes);
        summary.setPending(pending);
        int resolved = wins + losses;
        summary.setWinRate(resolved > 0 ? (double) wins / resolved : null);

        PaginationDto pagination = new PaginationDto();
        pagination.setPage(page);
        pagination.setPerPage(perPage);
        pagination.setTotal(total);

        HistoryResponse resp = new HistoryResponse();
        resp.setSummary(summary);
        resp.setPicks(picks);
        resp.setPagination(pagination);
        return resp;
    }

    // Outcome logic — from CLAUDE.md
    static String computeOutcome(String market, String side,
                                  Integer homeScore, Integer awayScore, String status) {
        if (!"final".equals(status) || homeScore == null || awayScore == null) {
            return "PENDING";
        }
        if ("moneyline".equals(market)) {
            boolean homeWon = homeScore > awayScore;
            return ("home".equals(side) == homeWon) ? "WIN" : "LOSS";
        }
        if ("run_line".equals(market)) {
            double margin = homeScore - awayScore;
            boolean homeCovered = margin > 1.5;
            return ("home".equals(side) == homeCovered) ? "WIN" : "LOSS";
        }
        return "PENDING";
    }
}
```

---

## Controller

```java
// controller/HistoryController.java
@RestController
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping("/history")
    public HistoryResponse getHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate start_date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate end_date,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int per_page) {

        LocalDate start = start_date != null ? start_date : LocalDate.now().minusDays(30);
        LocalDate end   = end_date   != null ? end_date   : LocalDate.now();

        return historyService.getHistory(start, end, page, per_page);
    }
}
```

---

## Crawl optimization (introducing numeric IDs + cursor pagination)

OFFSET pagination (`LIMIT/OFFSET`) becomes slow for “crawl everything” use-cases because the DB must still scan/skip earlier rows as `page` grows. To optimize crawling, introduce **stable numeric identifiers** on crawled tables and switch `/history` to **cursor-based** pagination.

### Schema changes (recommended approach)

Add a surrogate numeric ID on tables that the crawler pages through frequently, while keeping the existing natural keys unique:

- `games`: add `game_row_id BIGSERIAL PRIMARY KEY`, keep `game_id` as `UNIQUE NOT NULL`
- `slate_runs`: add `slate_run_row_id BIGSERIAL PRIMARY KEY`, keep `slate_run_id UUID UNIQUE NOT NULL` and `run_date UNIQUE`
- `recommendations`: add `rec_row_id BIGSERIAL PRIMARY KEY`, keep `rec_id UUID UNIQUE NOT NULL`
- (Optional) `odds_snapshots`, `predictions` similarly if you plan to crawl them directly

This yields fast “resume crawling from last seen ID” queries and avoids deep offsets.

### API shape changes

Add optional cursor params to `/history`:

- `cursor` (string): encodes the last seen `(created_at, rec_row_id)` or simply `rec_row_id`
- `limit` (int): replaces `per_page` for crawl mode (keep `page/per_page` for backwards compatibility if desired)

### Query strategy (post-migration)

- Use an indexed ordering key (e.g. `created_at DESC, rec_row_id DESC`), and filter with `WHERE (created_at, rec_row_id) < (:cursorCreatedAt, :cursorRecRowId)` for descending pagination.
- Keep the existing “dedupe latest per (game, market, side) per run” logic, but ensure the crawl ordering is stable and uses the numeric ID as the tie-breaker.

### Indexes to add

- `recommendations(created_at DESC, rec_row_id DESC)`
- `recommendations(slate_run_id, game_id, market, side, created_at DESC)`
- `slate_runs(run_date)`
- `games(game_id)` (already PK/unique)

This section is intentionally “plan-level”: it’s a DB migration and client pagination change, so implement it as a dedicated follow-up before large-scale crawling.

---

## Unit tests (Mockito)

**File:** `src/test/java/com/bettingcopilot/api/service/HistoryServiceTest.java`
**Annotations:** `@ExtendWith(MockitoExtension.class)`, `@Tag("unit")`

### `computeOutcome` — pure logic, no mocks needed

| Test | Input | Expected |
|------|-------|----------|
| `moneyline_win_homeSide` | market=moneyline, side=home, home 4–2, final | WIN |
| `moneyline_loss_homeSide` | market=moneyline, side=home, home 2–4, final | LOSS |
| `moneyline_win_awaySide` | market=moneyline, side=away, home 2–4, final | WIN |
| `runLine_win_homeSide` | market=run_line, side=home, home 4–2 (margin 2), final | WIN |
| `runLine_loss_homeSide_marginOne` | market=run_line, side=home, home 3–2 (margin 1), final | LOSS |
| `runLine_win_awaySide` | market=run_line, side=away, home 3–2 (margin 1), final | WIN |
| `pending_scheduledGame` | status=scheduled | PENDING |
| `pending_nullScores` | status=final, homeScore=null | PENDING |

### Service-level (with mocks)

| Test | Setup | Assert |
|------|-------|--------|
| `getHistory_winRateCalculation` | 3 wins, 1 loss, 2 pending recs | summary.winRate == 0.75; pending == 2 |
| `getHistory_excludesNoBetDecision` | `recRepo.findHistoryPicks` pre-filtered by DB query | Only `medium` picks returned |
| `getHistory_paginationMath` | total=5, page=2, perPage=2 | pagination.page==2, total==5 |

---

## Integration tests (JUnit 5 + Testcontainers)

**File:** `src/test/java/com/bettingcopilot/api/integration/HistoryControllerIT.java`
**Extends:** `AbstractIntegrationTest`
**Annotations:** `@Tag("integration")`

### Seed data setup
- 2 slate_runs on different dates
- 3 games: 2 `final` (with scores), 1 `scheduled`
- 5 recommendations: 3 `medium` + 2 `no_bet`, covering both dates

| Test | Request | Assert |
|------|---------|--------|
| `getHistory_onlyMediumDecisionsReturned` | `GET /history` | picks.length == 3 (no_bet excluded) |
| `getHistory_correctOutcomeCounts` | `GET /history` | summary wins/losses/pending match seeded data |
| `getHistory_dateRangeFilter` | `GET /history?start_date=X&end_date=X` (single date) | Only picks from that slate_run returned |
| `getHistory_pagination` | `GET /history?page=2&per_page=2` | 1 item returned (3 total, page 2 of 2-per-page) |

---

## Verification

```bash
curl "http://localhost:8080/history"
curl "http://localhost:8080/history?start_date=2026-04-01&end_date=2026-04-29"
curl "http://localhost:8080/history?page=2&per_page=5"
```
