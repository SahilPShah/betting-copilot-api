# Plan: GET /slate + GET /slate/{date}

*Prerequisite: `plan_shared.md` (entities, repos, test infra)*

---

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/slate` | Today's picks (delegates to /slate/{today}) |
| GET | `/slate/{date}` | Picks for a specific date (`YYYY-MM-DD`) |

---

## Files to create

```
dto/
├── PickDto.java
├── NoBetDto.java
└── SlateResponse.java
service/
└── SlateService.java
controller/
└── SlateController.java
```

---

## DTOs

```java
// dto/PickDto.java
@Getter @Setter
public class PickDto {
    private String gameId;
    private String homeTeam;
    private String awayTeam;
    private OffsetDateTime firstPitchUtc;
    private String market;
    private String side;
    private Integer odds;
    private Double edge;
    private Double confidence;
    private Double modelProb;
    private Double impliedProb;
    private String decision;
    private String homeStarter;
    private Double homeStarterEra;
    private Double homeStarterL3Era;
    private Double homeStarterWhip;
    private String awayStarter;
    private Double awayStarterEra;
    private Double awayStarterL3Era;
    private Double awayStarterWhip;
    private String llmExplanation;
}

// dto/NoBetDto.java
@Getter @Setter
public class NoBetDto {
    private String gameId;
    private String reason;
}

// dto/SlateResponse.java
@Getter @Setter
public class SlateResponse {
    private LocalDate runDate;
    private String modelVersion;
    private Integer gamesCount;
    private Integer picksCount;
    private OffsetDateTime ranAt;
    private List<PickDto> picks;
    private List<NoBetDto> noBets;
}
```

---

## Service

```java
// service/SlateService.java
@Service
public class SlateService {

    private final SlateRunRepository slateRunRepo;
    private final RecommendationRepository recRepo;
    private final GameRepository gameRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SlateResponse getSlate(LocalDate date) {
        SlateRun run = slateRunRepo.findByRunDate(date)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No slate run found for date: " + date));

        List<Recommendation> recs = recRepo.findDeduplicatedBySlateRunId(run.getSlateRunId());

        // Load games for the run's game IDs (batch fetch)
        Set<String> gameIds = recs.stream().map(Recommendation::getGameId).collect(toSet());
        Map<String, Game> gameMap = gameRepo.findAllById(gameIds)
            .stream().collect(toMap(Game::getGameId, g -> g));

        List<PickDto> picks = new ArrayList<>();
        List<NoBetDto> noBets = new ArrayList<>();

        for (Recommendation rec : recs) {
            if ("medium".equals(rec.getDecision())) {
                picks.add(toPickDto(rec, gameMap.get(rec.getGameId())));
            } else if ("no_bet".equals(rec.getDecision())) {
                NoBetDto nb = new NoBetDto();
                nb.setGameId(rec.getGameId());
                nb.setReason(rec.getNoBetReason());
                noBets.add(nb);
            } else {
                // Unknown decision value — ignore (forward compatibility)
            }
        }

        SlateResponse resp = new SlateResponse();
        resp.setRunDate(run.getRunDate());
        resp.setModelVersion(run.getModelVersion());
        resp.setGamesCount(run.getGamesCount());
        resp.setPicksCount(run.getPicksCount());
        resp.setRanAt(run.getRanAt());
        resp.setPicks(picks);
        resp.setNoBets(noBets);
        return resp;
    }

    private PickDto toPickDto(Recommendation rec, Game game) {
        PickDto dto = new PickDto();
        dto.setGameId(rec.getGameId());
        dto.setMarket(rec.getMarket());
        dto.setSide(rec.getSide());
        dto.setEdge(rec.getEdge());
        dto.setConfidence(rec.getConfidence());
        dto.setDecision(rec.getDecision());
        dto.setLlmExplanation(rec.getLlmExplanation());

        if (game != null) {
            dto.setHomeTeam(game.getHomeTeamId());
            dto.setAwayTeam(game.getAwayTeamId());
            dto.setFirstPitchUtc(game.getFirstPitchUtc());
        }

        if (rec.getContextSnapshot() != null) {
            try {
                JsonNode ctx = objectMapper.readTree(rec.getContextSnapshot());
                dto.setModelProb(ctx.path("model_prob").asDouble());
                dto.setImpliedProb(ctx.path("implied_prob").asDouble());
                dto.setOdds(ctx.path("american_odds").asInt());

                JsonNode hs = ctx.path("home_starter");
                if (!hs.isMissingNode() && !hs.isNull()) {
                    dto.setHomeStarter(hs.path("name").asText(null));
                    dto.setHomeStarterEra(hs.path("era").asDouble());
                    dto.setHomeStarterL3Era(hs.path("l3_era").asDouble());
                    dto.setHomeStarterWhip(hs.path("whip").asDouble());
                }

                JsonNode as = ctx.path("away_starter");
                if (!as.isMissingNode() && !as.isNull()) {
                    dto.setAwayStarter(as.path("name").asText(null));
                    dto.setAwayStarterEra(as.path("era").asDouble());
                    dto.setAwayStarterL3Era(as.path("l3_era").asDouble());
                    dto.setAwayStarterWhip(as.path("whip").asDouble());
                }
            } catch (JsonProcessingException e) {
                // older record shape — leave fields null
            }
        }
        return dto;
    }
}
```

---

## Controller

```java
// controller/SlateController.java
@RestController
public class SlateController {

    private final SlateService slateService;

    @GetMapping("/slate")
    public SlateResponse getToday() {
        return slateService.getSlate(LocalDate.now());
    }

    @GetMapping("/slate/{date}")
    public SlateResponse getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return slateService.getSlate(date);
    }
}
```

---

## Unit tests (Mockito)

**File:** `src/test/java/com/bettingcopilot/api/service/SlateServiceTest.java`
**Annotations:** `@ExtendWith(MockitoExtension.class)`, `@Tag("unit")`

| Test | Setup | Assert |
|------|-------|--------|
| `getSlate_returnsPicksAndNoBets` | Slate run found; 1 rec decision=`medium`, 1 decision=`no_bet` | picks.size==1, noBets.size==1 |
| `getSlate_notFound_throws404` | `slateRunRepo.findByRunDate()` returns empty | `ResponseStatusException` with 404 status |
| `toPickDto_fullContextSnapshot_allFieldsMapped` | context_snapshot JSON includes home_starter + away_starter | All 8 starter fields populated on PickDto |
| `toPickDto_missingStarterFields_noException` | context_snapshot JSON has no home_starter/away_starter keys | Starter fields null, no exception thrown |
| `toPickDto_nullContextSnapshot_noNpe` | rec.contextSnapshot == null | Returns PickDto with null starter fields, no NPE |

---

## Integration tests (JUnit 5 + Testcontainers)

**File:** `src/test/java/com/bettingcopilot/api/integration/SlateControllerIT.java`
**Extends:** `AbstractIntegrationTest`
**Annotations:** `@Tag("integration")`

| Test | Seed data | Request | Assert |
|------|-----------|---------|--------|
| `getSlate_byDate_returnsCorrectResponse` | 1 slate_run (today) + 1 game + 2 recs (1 medium, 1 no_bet) | `GET /slate/{today}` | 200, picks.length==1, noBets.length==1, gameId matches |
| `getSlate_today_delegatesToCurrentDate` | Same seed | `GET /slate` | 200, same response as /slate/{today} |
| `getSlate_unknownDate_returns404` | No data for date | `GET /slate/1999-01-01` | 404 |

---

## Verification

```bash
curl http://localhost:8080/slate
curl http://localhost:8080/slate/2026-04-29
curl http://localhost:8080/slate/1999-01-01   # expect 404
```
