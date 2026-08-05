# Plan: GET /game/{gameId}

*Prerequisite: `plan_shared.md` (entities, repos, test infra)*

---

## Endpoint

| Method | Path | Description |
|--------|------|-------------|
| GET | `/game/{gameId}` | Full detail for a single game |

`gameId` format: `YYYY-MM-DD-HOME-AWAY-N` (e.g. `2026-04-29-CLE-TBR-1`)

---

## Files to create

```
dto/
├── PredictionDto.java
├── OddsLineDto.java
├── OddsDto.java
├── StarterDto.java
├── StartersDto.java
├── RecommendationSummaryDto.java
└── GameDetailResponse.java
service/
└── GameService.java
controller/
└── GameController.java
```

---

## DTOs

```java
// dto/PredictionDto.java
@Getter @Setter
public class PredictionDto {
    private Double homeWinProb;
    private Double awayWinProb;
    private Double predictedMargin;
    private Double predictedTotal;
    private Double homeCoverProb;
    private Double awayCoverProb;
    private Double eloDiff;
}

// dto/OddsLineDto.java
@Getter @Setter
public class OddsLineDto {
    private Integer americanOdds;
    private Double impliedProb;
    private Double point;       // null for moneyline; -1.5 or +1.5 for run_line
}

// dto/OddsDto.java
@Getter @Setter
public class OddsDto {
    private Map<String, OddsLineDto> moneyline;   // keys: "home", "away"
    private Map<String, OddsLineDto> runLine;     // keys: "home", "away"
}

// dto/StarterDto.java
@Getter @Setter
public class StarterDto {
    private String name;
    private Double era;
    private Double l3Era;
    private Double whip;
}

// dto/StartersDto.java
@Getter @Setter
public class StartersDto {
    private StarterDto home;
    private StarterDto away;
}

// dto/RecommendationSummaryDto.java
@Getter @Setter
public class RecommendationSummaryDto {
    private String market;
    private String side;
    private Double edge;
    private Double confidence;
    private String decision;
    private String llmExplanation;
}

// dto/GameDetailResponse.java
@Getter @Setter
public class GameDetailResponse {
    private String gameId;
    private LocalDate gameDate;
    private String status;
    private String homeTeam;
    private String awayTeam;
    private OffsetDateTime firstPitchUtc;      // nullable
    private PredictionDto prediction;          // nullable if no prediction row
    private OddsDto odds;
    private StartersDto starters;              // nullable if no context_snapshot
    private RecommendationSummaryDto recommendation;  // nullable
}
```

---

## Service

```java
// service/GameService.java
@Service
public class GameService {

    private final GameRepository gameRepo;
    private final PredictionRepository predictionRepo;
    private final OddsSnapshotRepository oddsRepo;
    private final RecommendationRepository recRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GameDetailResponse getGame(String gameId) {
        Game game = gameRepo.findById(gameId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Game not found: " + gameId));

        GameDetailResponse resp = new GameDetailResponse();
        resp.setGameId(game.getGameId());
        resp.setGameDate(game.getGameDate());
        resp.setStatus(game.getStatus());
        resp.setHomeTeam(game.getHomeTeamId());
        resp.setAwayTeam(game.getAwayTeamId());
        resp.setFirstPitchUtc(game.getFirstPitchUtc());

        // Prediction (nullable)
        predictionRepo.findByGameId(gameId).ifPresent(p -> {
            PredictionDto dto = new PredictionDto();
            dto.setHomeWinProb(p.getHomeWinProb());
            dto.setAwayWinProb(p.getAwayWinProb());
            dto.setPredictedMargin(p.getPredictedMargin());
            dto.setPredictedTotal(p.getPredictedTotal());
            dto.setHomeCoverProb(p.getHomeCoverProb());
            dto.setAwayCoverProb(p.getAwayCoverProb());
            dto.setEloDiff(p.getEloDiff());
            resp.setPrediction(dto);
        });

        // Odds — group into OddsDto
        List<OddsSnapshot> snapshots = oddsRepo.findLatestByGameId(gameId);
        resp.setOdds(toOddsDto(snapshots));

        // Starters + recommendation — from most recent rec for this game
        // Starters — from most recent rec for this game (any decision)
        recRepo.findTopByGameIdOrderByCreatedAtDesc(gameId).ifPresent(rec -> {
            resp.setStarters(parseStarters(rec.getContextSnapshot()));
        });

        // Recommendation — from most recent "medium" rec for this game (if any)
        recRepo.findTopByGameIdAndDecisionOrderByCreatedAtDesc(gameId, "medium").ifPresent(rec -> {
            RecommendationSummaryDto rDto = new RecommendationSummaryDto();
            rDto.setMarket(rec.getMarket());
            rDto.setSide(rec.getSide());
            rDto.setEdge(rec.getEdge());
            rDto.setConfidence(rec.getConfidence());
            rDto.setDecision(rec.getDecision());
            rDto.setLlmExplanation(rec.getLlmExplanation());
            resp.setRecommendation(rDto);
        });

        return resp;
    }

    private OddsDto toOddsDto(List<OddsSnapshot> snapshots) {
        OddsDto dto = new OddsDto();
        Map<String, OddsLineDto> moneyline = new HashMap<>();
        Map<String, OddsLineDto> runLine = new HashMap<>();

        for (OddsSnapshot s : snapshots) {
            OddsLineDto line = new OddsLineDto();
            line.setAmericanOdds(s.getAmericanOdds());
            line.setImpliedProb(s.getImpliedProb());
            line.setPoint(s.getRunLinePoint());

            if ("moneyline".equals(s.getMarket())) {
                moneyline.put(s.getSide(), line);
            } else if ("run_line".equals(s.getMarket())) {
                runLine.put(s.getSide(), line);
            }
        }

        dto.setMoneyline(moneyline);
        dto.setRunLine(runLine);
        return dto;
    }

    private StartersDto parseStarters(String contextSnapshot) {
        if (contextSnapshot == null) return null;
        try {
            JsonNode ctx = objectMapper.readTree(contextSnapshot);

            StartersDto dto = new StartersDto();
            JsonNode hs = ctx.path("home_starter");
            if (!hs.isMissingNode() && !hs.isNull()) {
                StarterDto s = new StarterDto();
                s.setName(hs.path("name").asText(null));
                s.setEra(hs.path("era").asDouble());
                s.setL3Era(hs.path("l3_era").asDouble());
                s.setWhip(hs.path("whip").asDouble());
                dto.setHome(s);
            }
            JsonNode as = ctx.path("away_starter");
            if (!as.isMissingNode() && !as.isNull()) {
                StarterDto s = new StarterDto();
                s.setName(as.path("name").asText(null));
                s.setEra(as.path("era").asDouble());
                s.setL3Era(as.path("l3_era").asDouble());
                s.setWhip(as.path("whip").asDouble());
                dto.setAway(s);
            }
            return dto;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
```

---

## Controller

```java
// controller/GameController.java
@RestController
public class GameController {

    private final GameService gameService;

    @GetMapping("/game/{gameId}")
    public GameDetailResponse getGame(@PathVariable String gameId) {
        return gameService.getGame(gameId);
    }
}
```

---

## Unit tests (Mockito)

**File:** `src/test/java/com/bettingcopilot/api/service/GameServiceTest.java`
**Annotations:** `@ExtendWith(MockitoExtension.class)`, `@Tag("unit")`

| Test | Setup | Assert |
|------|-------|--------|
| `getGame_fullResponse_allFieldsMapped` | All repos return data; rec has full context_snapshot | All nested DTOs populated |
| `getGame_notFound_throws404` | `gameRepo.findById()` returns empty | `ResponseStatusException` 404 |
| `getGame_noRecommendation_recommendationIsNull` | `recRepo.findTop...` returns empty | `resp.recommendation == null` |
| `getGame_noRecommendationMedium_recommendationIsNull` | Rec exists but decision=`no_bet` | `resp.recommendation == null`; starters still parsed |
| `toOddsDto_groupsMoneylineAndRunLine` | 4 snapshots: 2 markets × 2 sides | `moneyline.home`, `moneyline.away`, `runLine.home`, `runLine.away` all set |
| `parseStarters_missingFields_returnsNull` | context_snapshot JSON has no starter keys | `resp.starters == null` or fields null |

---

## Integration tests (JUnit 5 + Testcontainers)

**File:** `src/test/java/com/bettingcopilot/api/integration/GameControllerIT.java`
**Extends:** `AbstractIntegrationTest`
**Annotations:** `@Tag("integration")`

| Test | Seed data | Request | Assert |
|------|-----------|---------|--------|
| `getGame_fullDetail` | 1 game + 1 prediction + 4 odds_snapshots + 1 rec (medium, full context_snapshot) | `GET /game/{gameId}` | 200; prediction, odds (all 4 lines), starters, recommendation all present |
| `getGame_notFound` | No game with that ID | `GET /game/bad-id` | 404 |
| `getGame_noRecommendation` | 1 game + 1 prediction + 4 odds_snapshots; no recommendations row | `GET /game/{gameId}` | 200; `recommendation` field is null |

---

## Verification

```bash
curl http://localhost:8080/game/2026-04-29-CLE-TBR-1
curl http://localhost:8080/game/nonexistent-id   # expect 404
```
