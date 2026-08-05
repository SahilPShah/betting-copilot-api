# Betting Copilot API Reference

> **Interim documentation.** Once the server is deployed, the springdoc-generated OpenAPI spec
> (`GET /v3/api-docs`, Swagger UI at `/swagger-ui.html`) becomes the source of truth; this
> document is a hand-written reference until then.

All endpoints are read-only `GET`s returning JSON. Dates use `YYYY-MM-DD`. There is no
authentication.

---

## GET /health

Service health plus data freshness: confirms DB connectivity and reports the most recent game
and slate run dates.

```bash
curl http://localhost:8080/health
```

```json
{
  "status": "ok",
  "latestGameDate": "2026-04-29",
  "latestSlateDate": "2026-04-29"
}
```

- `latestGameDate` / `latestSlateDate` are `null` when the corresponding tables are empty.
- Infrastructure-level health (DB pool details, liveness) is at `GET /actuator/health`.

---

## GET /slate and GET /slate/{date}

The daily betting slate: recommended picks and skipped games for one slate run.
`GET /slate` is equivalent to `GET /slate/{today}`.

```bash
curl http://localhost:8080/slate/2026-04-29
```

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
      "firstPitchUtc": null,
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
      "homeStarterWhip": 1.167,
      "awayStarter": "Drew Rasmussen",
      "awayStarterEra": 3.21,
      "awayStarterL3Era": 3.67,
      "awayStarterWhip": 0.922,
      "llmExplanation": "..."
    }
  ],
  "noBets": [
    { "gameId": "2026-04-29-CHW-LAA-1", "reason": "negative_edge" }
  ]
}
```

- **404** — no slate run exists for the date.
- `decision` values: `medium` (selected pick) and `no_bet` (skipped). `noBets[].reason` is one of
  `negative_edge`, `below_edge_threshold`, `not_top_pick`, `low_confidence`, `efficiently_priced`, or `null`.
- Starter fields (`homeStarter*`, `awayStarter*`) are `null` for older records (pre-v4 runs).
- `firstPitchUtc` is frequently `null` in the source data.

---

## GET /game/{gameId}

Full detail for a single game. `gameId` format: `YYYY-MM-DD-HOME-AWAY-N`.

```bash
curl http://localhost:8080/game/2026-04-29-CLE-TBR-1
```

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
      "home": { "americanOdds": -122, "impliedProb": 0.526, "point": null },
      "away": { "americanOdds": 102,  "impliedProb": 0.474, "point": null }
    },
    "runLine": {
      "home": { "americanOdds": 169,  "impliedProb": 0.35, "point": -1.5 },
      "away": { "americanOdds": -206, "impliedProb": 0.65, "point": 1.5 }
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

- **404** — no game exists with that ID.
- `prediction`, `starters`, and `recommendation` are each `null` when no data exists:
  `recommendation` only appears when the game has a selected (`medium`) pick; `starters` come
  from the most recent recommendation's context data regardless of decision.
- `odds` reflects the latest snapshot per market/side; a market key maps to an empty object if
  no odds were captured.
- Game `status` is `scheduled` or `final`; scores are `null` until final.

---

## GET /history

Past picks with WIN/LOSS/PENDING outcomes computed from final scores. Only selected picks
(`decision = medium`) are included.

| Query param | Default | Description |
|-------------|---------|-------------|
| `start_date` | 30 days ago | Range start (inclusive), `YYYY-MM-DD` |
| `end_date` | today | Range end (inclusive), `YYYY-MM-DD` |
| `page` | `1` | 1-based page number |
| `per_page` | `20` | Results per page (clamped to 1–100) |

```bash
curl "http://localhost:8080/history?start_date=2026-04-01&end_date=2026-04-29&page=1&per_page=20"
```

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
  "pagination": { "page": 1, "perPage": 20, "total": 25 }
}
```

- `summary` covers the **entire date range**, while `picks` contains only the current page.
- Outcome rules: moneyline — the picked side won; run line — home covers when winning by 2+
  (spread is always ±1.5). Games not yet `final` are `PENDING`.
- `winRate` = wins / (wins + losses), excluding pending and pushes; `null` when nothing has
  resolved.
- A page past the end returns an empty `picks` array (not an error).

---

## Operational endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /actuator/health` | Liveness + DB connectivity (`{"status":"UP", ...}`) |
| `GET /actuator/metrics` | JVM / HTTP / connection pool metrics |
| `GET /v3/api-docs` | OpenAPI 3 spec (JSON; `.yaml` variant available) |
| `GET /swagger-ui.html` | Interactive API explorer |

## Errors

Errors use Spring's default JSON shape:

```json
{ "timestamp": "...", "status": 404, "error": "Not Found", "path": "/slate/1999-01-01" }
```

| Status | When |
|--------|------|
| 404 | Unknown `gameId`, or no slate run for the requested date |
| 400 | Malformed date or non-numeric paging parameter |
