# TODO

Remaining work, roughly in priority order. The four core endpoints (`/health`, `/slate`,
`/game/{gameId}`, `/history`) are implemented and tested; what's left is deployment, hardening,
and nice-to-haves.

## Deployment (next up)

- [ ] Create a DigitalOcean Container Registry and add `DO_REGISTRY_USER` / `DO_REGISTRY_TOKEN`
      as GitHub Actions secrets
- [ ] Un-stub the registry push step in `.github/workflows/ci.yml` (currently commented out)
- [ ] First deploy to the Droplet: pull the image and run it with `DATABASE_URL`
      (`--restart unless-stopped`, port 8080)
- [ ] Create a **read-only Postgres user** for the API instead of connecting as `doadmin`
- [ ] Put the API behind HTTPS (Caddy/nginx reverse proxy or DO load balancer)
- [ ] Smoke-check after deploy: run the contract suite against the live URL
      (`./mvnw -pl integration-tests -am verify -Pcontract -Dapi.baseUrl=https://<host>`)

## CI/CD

- [ ] Add a deploy job to CI (SSH to Droplet → pull image → restart container) triggered on
      `main` after the image push
- [ ] Run the contract tests as a post-deploy CI step against the deployed URL
- [ ] Consider a JaCoCo coverage threshold once a baseline is established (report-only today);
      IT coverage is not aggregated into the app report — add `report-aggregate` if that matters
- [ ] Tag releases / version the image with the git SHA instead of `0.0.1-SNAPSHOT`

## Documentation

- [ ] After deployment, make the springdoc-generated OpenAPI spec (`/v3/api-docs`, Swagger UI)
      the source of truth and retire `docs/api.md` (it is marked interim)
- [ ] Retire the historical planning docs (`docs/plan_*.md`, `docs/api_design.md`) or move them
      to an archive folder — CLAUDE.md and `docs/tech_stack.md` supersede them

## API features (not yet built)

- [ ] **Team names**: responses return 3-letter IDs (`TBR`); join the `teams` table to expose
      `full_name` and `division`
- [ ] New endpoints over tables the API doesn't use yet:
  - [ ] Team stats (`team_season_stats`, `team_stats_mlb` — last-7-day rolling stats)
  - [ ] Injuries per game (`injury_statuses`, with `impact_score`)
  - [ ] Pitcher game logs (`pitcher_game_logs`)
- [ ] **Cursor pagination for `/history`** (see the crawl-optimization section of
      `docs/plan_history.md`): needs numeric-ID DB migration owned by the Python pipeline —
      coordinate before large-scale crawling; current OFFSET paging is fine at today's volumes
- [ ] HTTP caching: slates are immutable once final — add `Cache-Control`/`ETag` on
      `/slate/{date}` and `/game/{gameId}` for past dates
- [ ] CORS configuration when a frontend exists

## Hardening / operations

- [ ] Lock down Actuator: `/actuator/health` is fine public, but restrict `metrics`/`info` or
      bind management to a separate port
- [ ] Auth / rate limiting if the API is ever exposed beyond personal use (currently open by
      design)
- [ ] Connection pool sizing (Hikari defaults today; DO managed PG has a connection cap shared
      with the Python job)
- [ ] Structured logging + log shipping once deployed

## Tech debt

- [ ] Remove the Groovy 4 pin in `integration-tests/pom.xml` when REST Assured supports the
      Groovy 5 that Spring Boot 4's BOM manages
- [ ] Revisit palantir-java-format (preferred formatting) when it supports JDK 25 javac
      internals; currently using google-java-format AOSP via Spotless
- [ ] springdoc still pulls Jackson 2 transitively alongside Boot 4's Jackson 3 — drop the
      duplicate once springdoc fully migrates
