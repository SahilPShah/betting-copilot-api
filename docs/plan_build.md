# Plan: Build + Docker + Deployment

---

## pom.xml changes

### 1. Add Spring Actuator + Testcontainers dependencies

```xml
<!-- Actuator (add to <dependencies>) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Testcontainers (test scope) -->
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

### 2. Add Testcontainers BOM to `<dependencyManagement>`

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-bom</artifactId>
            <version>1.20.4</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 3. Maven Surefire — unit tests only (`mvn test`)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <groups>unit</groups>
    </configuration>
</plugin>
```

### 4. Maven Failsafe — integration tests (`mvn verify`)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <groups>integration</groups>
        <!-- Failsafe looks for *IT.java by default; this ensures @Tag is also respected -->
        <includes>
            <include>**/*IT.java</include>
        </includes>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## Build commands

```bash
# Unit tests only — no Docker required
mvn test

# Unit + integration tests — Docker must be running (Testcontainers)
mvn verify

# Build fat JAR, skip all tests
mvn clean package -DskipTests

# Build fat JAR + run unit tests
mvn clean package
```

Output JAR: `target/betting-copilot-api-0.0.1-SNAPSHOT.jar`

---

## Dockerfile

Multi-stage build. Final image contains only the JRE — no Maven, no source code.

```dockerfile
# ── Stage 1: build ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Cache dependency layer separately from source
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: runtime ────────────────────────────────────────────
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Place `Dockerfile` at the repo root.

### .dockerignore

```
target/
.git/
*.md
docs/
src/test/
.env
.env.*
```

---

## Deployment on Droplet

### One-time setup on the Droplet

```bash
# Set DATABASE_URL in the deploy user's shell profile
echo 'export DATABASE_URL="jdbc:postgresql://doadmin:PASSWORD@betting-copilot-nyc1-do-user-36545166-0.f.db.ondigitalocean.com:25060/betting_copilot?sslmode=require"' >> ~/.bashrc
source ~/.bashrc
```

### Build and deploy

```bash
# On your local machine — build and push image (or build on the Droplet directly)
docker build -t betting-copilot-api .

# On the Droplet — run container
docker run -d \
  -e DATABASE_URL=$DATABASE_URL \
  -p 8080:8080 \
  --name betting-copilot \
  --restart unless-stopped \
  betting-copilot-api
```

### Updating a running container

```bash
docker stop betting-copilot && docker rm betting-copilot
docker build -t betting-copilot-api .
docker run -d -e DATABASE_URL=$DATABASE_URL -p 8080:8080 --name betting-copilot --restart unless-stopped betting-copilot-api
```

---

## Verify deployment

```bash
# Health (Spring Actuator — auto-configured, checks DB connectivity)
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP","components":{"db":{"status":"UP",...}}}

# Metrics
curl http://localhost:8080/actuator/metrics

# Core endpoints
curl http://localhost:8080/slate
curl http://localhost:8080/game/2026-04-29-CLE-TBR-1
curl "http://localhost:8080/history?start_date=2026-04-01&end_date=2026-04-29"
```

---

## Future: CI/CD sketch

When ready to add automation:

1. **GitHub Actions** — on push to `main`:
   - `mvn test` (unit, no Docker)
   - Build Docker image
   - Push to DigitalOcean Container Registry (or Docker Hub)
   - SSH to Droplet, pull image, restart container

2. `DATABASE_URL` stored as a GitHub Actions secret — never in the repo.
