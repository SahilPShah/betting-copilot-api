# betting-copilot-api

Read-only Spring Boot REST API over the Betting Copilot PostgreSQL database. All predictions
and betting recommendations are pre-computed daily by a Python pipeline and stored in the DB —
this service never runs ML and never writes; it is SQL queries wrapped in JSON.

**Stack**: Java 25 · Spring Boot 4 · Spring Data JPA/Hibernate · PostgreSQL (DigitalOcean
Managed) · springdoc-openapi · built with Maven (wrapper included), shipped as a Buildpacks
container image. Details in [docs/tech_stack.md](docs/tech_stack.md).

## Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /health` | DB connectivity + latest game/slate dates |
| `GET /slate`, `GET /slate/{date}` | Daily slate: recommended picks and skipped games |
| `GET /game/{gameId}` | Full game detail: prediction, latest odds, starters, recommendation |
| `GET /history` | Past picks with WIN/LOSS/PENDING outcomes, summary, pagination |
| `GET /actuator/health`, `/actuator/metrics` | Infrastructure health and metrics |

Full request/response reference: [docs/api.md](docs/api.md). With the app running, interactive
docs are at `/swagger-ui.html` and the OpenAPI spec at `/v3/api-docs` (generated from code by
springdoc).

Remaining work is tracked in [TODO.md](TODO.md).

## Project layout

```
app/                  Spring Boot application + unit tests
integration-tests/    Testcontainers integration tests + REST Assured contract tests
docs/                 tech_stack.md, api.md (+ historical planning docs)
.github/workflows/    CI (build, test, image)
```

## Local development

### Prerequisites

- **JDK 25** (the build enforces this; `java -version` to check)
- **Docker** — only needed for integration tests and container builds
- No Maven install needed — use the bundled wrapper `./mvnw`

### Run the server

The only configuration is the `DATABASE_URL` environment variable. Note that pgJDBC requires
credentials as query parameters (not `user:pass@host`):

```bash
export DATABASE_URL="jdbc:postgresql://<host>:25060/betting_copilot?sslmode=require&user=doadmin&password=<PASSWORD>"
./mvnw -pl app spring-boot:run
```

Then:

```bash
curl http://localhost:8080/health
curl http://localhost:8080/slate
curl "http://localhost:8080/history?start_date=2026-04-01&end_date=2026-04-29"
```

### Run without the production DB

To develop against an empty local database instead of DigitalOcean:

```bash
docker run -d --name bc-pg -e POSTGRES_DB=betting_copilot -e POSTGRES_USER=test \
  -e POSTGRES_PASSWORD=test -p 5432:5432 postgres:16
docker exec -i bc-pg psql -U test -d betting_copilot < integration-tests/src/test/resources/schema.sql

export DATABASE_URL="jdbc:postgresql://localhost:5432/betting_copilot?user=test&password=test"
./mvnw -pl app spring-boot:run
```

### Tests

```bash
./mvnw test        # unit tests only — fast, no Docker
./mvnw verify      # + Testcontainers integration tests (Docker) + formatting check + coverage
```

Integration tests spin up their own throwaway PostgreSQL via Testcontainers — no local DB
setup, and they never touch the production database.

Contract tests (smoke tests against a **running** server):

```bash
./mvnw -pl integration-tests -am verify -Pcontract -Dapi.baseUrl=http://localhost:8080
```

### Formatting

Spotless (google-java-format, AOSP style) is enforced on `verify` and in CI:

```bash
./mvnw spotless:apply   # fix formatting before committing
```

## Container image

Built with Cloud Native Buildpacks — there is no Dockerfile:

```bash
./mvnw -pl app spring-boot:build-image -DskipTests
docker run -e DATABASE_URL="$DATABASE_URL" -p 8080:8080 betting-copilot-api:0.0.1-SNAPSHOT
```

## CI

GitHub Actions (`.github/workflows/ci.yml`): every push/PR runs the formatting check and the
full test suite (Testcontainers works on hosted runners); pushes to `main` additionally build
the container image. Registry push and deployment are pending (see TODO.md). Dependabot files
weekly dependency-update PRs.

## Database

The schema is owned by the Python pipeline — this API must never modify it
(`ddl-auto=none`, no `@GeneratedValue`, exact column naming). Key tables: `slate_runs`,
`recommendations`, `games`, `predictions`, `odds_snapshots`. Gotchas (documented in
CLAUDE.md): `decision` values are `medium`/`no_bet`, recommendations may be duplicated on
re-runs (dedupe with `DISTINCT ON`), and `context_snapshot` JSONB fields are optional on older
records.
