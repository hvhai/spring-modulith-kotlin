# spring-modulith-kotlin

Spring Boot application written in Kotlin and organized with Spring Modulith. The project contains a Todo API and a fruit-ordering flow, with a Thymeleaf web UI.

## Stack

- Java 21, Kotlin 1.9.25
- Spring Boot 3.4.1
- Spring Modulith 1.3.1
- Spring MVC, Thymeleaf, Spring Security OAuth2/Auth0
- Spring Data JPA, Flyway, H2 and MySQL
- OpenAPI/Swagger UI, Actuator, and Grafana LGTM observability (Prometheus, Tempo, Loki, Pyroscope)
- Gradle 8.11.1 (wrapper)

## Prerequisites

- JDK 21
- Docker Desktop for MySQL, the observability stack, or Testcontainers-based tests

## Run locally

```shell
./gradlew bootRun
```

On Windows:

```powershell
.\gradlew.bat bootRun
```

The application listens on `http://localhost:8080`. OAuth2 client settings use `CLIENT_ID` and `CLIENT_SECRET`; configure these when exercising authenticated browser flows.

## Tests

```powershell
.\gradlew.bat test       # Windows
./gradlew test            # Linux/macOS
./gradlew check           # tests plus other verification tasks
```

Test reports are generated at `build/reports/tests/test/index.html`.

## Infrastructure

```shell
docker compose up -d
```

| Service | URL | Purpose |
|---|---|---|
| MySQL | `localhost:3316` | database |
| Grafana | http://localhost:3000 | single observability UI (anonymous access) |
| Prometheus | http://localhost:9090 | metrics, scrapes `/actuator/prometheus` |
| Tempo | http://localhost:3200 | traces, OTLP on 4317/4318 |
| Loki | http://localhost:3100 | logs |
| Pyroscope | http://localhost:4040 | continuous CPU/JFR profiling |

### Run modes

- **App on the host** — `docker compose up -d` for infrastructure, then
  `./gradlew bootRun --args='--spring.profiles.active=local'`.
  The `local` profile enables tracing and the Loki log appender.
  **Note:** Continuous profiling works on Linux/macOS hosts only; on Windows, use containerized mode.
- **Everything containerized** — `docker compose -f docker-compose-full.yml up`.
  Profiling is fully functional in this mode on all platforms.

The two modes publish the same host ports, so stop one before starting the other.
See [`docs/deployment-guide.md`](docs/deployment-guide.md) for trace/log correlation and profiling details.

## Docker image

```shell
docker build . --tag spring-modulith-kotlin:latest --platform=linux/amd64
docker run --rm -p 8080:8080 spring-modulith-kotlin:latest
```

See [`docs/`](docs/) for architecture, development standards, deployment notes, and the roadmap.
