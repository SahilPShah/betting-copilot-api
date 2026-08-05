# Tech Stack

Definitive reference for the technologies used by the Betting Copilot API server.

## Application

| Layer | Choice | Notes |
|-------|--------|-------|
| Language | Java 25 | Enforced by the Maven Enforcer plugin |
| Framework | Spring Boot 4.0.x | `spring-boot-starter-parent` manages all dependency versions |
| Web | Spring MVC (`spring-boot-starter-web`) | Servlet stack on embedded Tomcat |
| DB access | Spring Data JPA + Hibernate | Repositories with derived queries; native `DISTINCT ON` queries for PostgreSQL-specific deduplication |
| Database | PostgreSQL (DigitalOcean Managed) | Connection via `DATABASE_URL` env var; SSL required in production |
| JSON | Jackson 3 (`tools.jackson.*`) | Spring Boot 4 ships Jackson 3 — note the new package namespace (`tools.jackson.databind.ObjectMapper`), not `com.fasterxml.*` |
| Boilerplate | Lombok | `@Getter`/`@Setter` on entities and DTOs; registered explicitly in `annotationProcessorPaths` (required on JDK 23+) |
| API docs | springdoc-openapi | Generated from `@Operation`/`@Schema` annotations; Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs` |
| Monitoring | Spring Boot Actuator | `/actuator/health` (DB connectivity), `/actuator/metrics` |

## Read-only database constraints

The database is owned by the Python pipeline; this API never writes.

- `spring.jpa.hibernate.ddl-auto=none` — Hibernate never touches the schema
- `PhysicalNamingStrategyStandardImpl` — column names used exactly as written (no camelCase conversion)
- No `@GeneratedValue` on any entity — IDs are always set by the Python job

## Testing

| Kind | Module | Tooling | Command |
|------|--------|---------|---------|
| Unit (`@Tag("unit")`) | `app` | JUnit 5 + Mockito, Surefire | `./mvnw test` (no Docker) |
| Integration (`@Tag("integration")`, `*IT.java`) | `integration-tests` | Testcontainers (postgres:16) + `RestTestClient`, Failsafe | `./mvnw verify` (Docker required) |
| Contract (`@Tag("contract")`) | `integration-tests` | REST Assured against a **running server** | `./mvnw -pl integration-tests -am verify -Pcontract -Dapi.baseUrl=http://localhost:8080` |

Integration tests share a singleton PostgreSQL container (`AbstractIntegrationTest`); the schema is applied from `integration-tests/src/test/resources/schema.sql`. `RestTestClient` is Spring Boot 4's replacement for the removed `TestRestTemplate`. The `integration-tests` module depends on the app's plain jar — the runnable fat jar is published with an `-exec` classifier (`app/target/betting-copilot-api-<version>-exec.jar`).

## Build & CI

| Concern | Choice |
|---------|--------|
| Build | Maven multi-module (`app`, `integration-tests`) via the **Maven Wrapper** (`./mvnw`, Maven 3.9.x pinned) |
| Toolchain guard | `maven-enforcer-plugin` — requires Java 25 and Maven 3.9+ |
| Reproducible builds | `project.build.outputTimestamp` set in the parent POM |
| Formatting | Spotless + google-java-format (AOSP style); `./mvnw spotless:apply` to fix, `spotless:check` runs on `verify` |
| Coverage | JaCoCo report on `verify` → `app/target/site/jacoco/` (no threshold enforced yet) |
| Container image | Cloud Native Buildpacks via `./mvnw -pl app spring-boot:build-image` — no Dockerfile; produces `betting-copilot-api:<version>` with layered OCI image and SBOM |
| CI | GitHub Actions (`.github/workflows/ci.yml`): formatting check + full `verify` (Testcontainers works on hosted runners) on every push/PR; image build on `main`. Registry push stubbed until DO credentials exist |
| Dependency updates | Dependabot weekly (Maven + GitHub Actions) |

## Running locally

```bash
export DATABASE_URL="jdbc:postgresql://<host>:25060/betting_copilot?sslmode=require&user=doadmin&password=<PASSWORD>"
./mvnw -pl app spring-boot:run
```

Note: pgJDBC does not accept `user:password@host` URLs — credentials go in query params (or `spring.datasource.username`/`password`).

Container:

```bash
./mvnw -pl app spring-boot:build-image -DskipTests
docker run -e DATABASE_URL="$DATABASE_URL" -p 8080:8080 betting-copilot-api:0.0.1-SNAPSHOT
```
