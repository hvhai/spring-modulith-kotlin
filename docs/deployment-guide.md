# Deployment Guide

## Requirements

- Java 21 for direct JVM execution, or Docker for container deployment.
- Auth0/OIDC application credentials for authenticated flows.
- H2 for the default in-memory setup, or MySQL for a persistent deployment.
- The Grafana LGTM observability stack (Prometheus, Tempo, Loki, Pyroscope, Grafana) is optional for running the app, but required for metrics, traces, logs, and profiling.

## Local supporting services

```shell
docker compose up -d
```

This starts MySQL on host port `3326`, Prometheus on `9090`, Tempo on `3200`, Loki on `3100`, Pyroscope on `4040`, and Grafana on `3000`. Supply `MYSQL_ROOT_PASSWORD` in the environment before starting Compose.

Grafana automatically provisions the `Spring Boot Observability` dashboard in both Compose modes. Fresh anonymous sessions default to the light theme; an existing browser session may retain its saved theme preference.

Use [`.env.example`](../.env.example) as a starter template. Copy it to `.env` and replace placeholder values before using Compose. Direct Gradle and JVM runs do not load `.env`; export the required values in the shell.

## Build and run with Gradle

```shell
./gradlew clean build
./gradlew bootRun --args='--spring.profiles.active=local'
```

The `local` profile connects the host application to MySQL, Tempo, and Loki from `docker-compose.yml`. The default profile uses in-memory H2 and leaves tracing disabled.

## Build and run with Docker

```shell
docker build . --tag spring-modulith-kotlin:latest --platform=linux/amd64
docker run --rm --env-file .env -p 8080:8080 spring-modulith-kotlin:latest
```

The image exposes port 8080 and starts the packaged Spring Boot JAR. The Dockerfile skips tests during image construction, so run `./gradlew clean check` as a separate release gate.

The runtime stage uses `eclipse-temurin:21.0.12_8-jre-noble`. A JRE is sufficient — the app runs a Spring Boot fat jar and needs no compiler at runtime; the `jdk.jfr` module used by the request tracing interceptor is present in Temurin JRE images. The previous `openjdk:21-jdk-slim` base was removed from Docker Hub and no longer receives security updates.

The build stage (`gradle:8.11-jdk21-alpine`, musl) and the runtime stage (glibc) intentionally use different C libraries. Only the compiled JAR crosses between stages, and bytecode is libc-neutral, so the two do not need to match.

## Configuration

Start from [`.env.example`](../.env.example). Spring defaults and the `local` overrides live in [`application.properties`](../src/main/resources/application.properties) and [`application-local.properties`](../src/main/resources/application-local.properties). Container-specific values and overrides live in [`docker-compose-full.yml`](../docker-compose-full.yml).

Never place real credentials in source control or image layers.

## Operational checks

- Application: `http://localhost:8080`
- H2 console (development): `/h2-console`
- Swagger UI: `/swagger-ui/index.html`
- Actuator endpoints: `/actuator/**`
- Prometheus metrics endpoint: `/actuator/prometheus`
- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090` (Status -> Targets)
- Tempo: `http://localhost:3200`
- Loki: `http://localhost:3100`
- Pyroscope: `http://localhost:4040`

## Trace and log correlation

Grafana correlates traces and logs bidirectionally via the traceId. To view a trace with its associated logs:

1. Navigate to Explore in Grafana.
2. Select Tempo as the datasource.
3. Find a trace by service, operation, or duration.
4. Open the trace detail panel and look for "Logs for this span" — this links to the corresponding log entries in Loki.

To view logs and find the associated trace:

1. Navigate to Explore in Grafana.
2. Select Loki as the datasource.
3. Query logs with label filter `{app="spring-modulith-kotlin"}`.
4. Expand a log line and look for "View Trace" — this links to the trace in Tempo.

## Continuous profiling

Profiling via Pyroscope collects CPU and Java Flight Recorder (JFR) data from the running application. The container image attaches the agent, keeps it disabled by default, and `docker-compose-full.yml` enables it and supplies the Pyroscope address.

The Gradle `bootRun` task does not attach the agent. For host profiling, build the executable jar and launch it with the checked-in agent after exporting `SPRING_PROFILES_ACTIVE=local`, `PYROSCOPE_AGENT_ENABLED=true`, `PYROSCOPE_APPLICATION_NAME=spring-modulith-kotlin`, and `PYROSCOPE_SERVER_ADDRESS=http://localhost:4040`:

```shell
./gradlew bootJar
java -javaagent:libs/agent-pyroscope-2.1.2.jar -jar build/libs/spring-modulith-kotlin-0.0.1-SNAPSHOT.jar
```

On Windows, use `JFR` because the default `ASYNC` profiler has no Windows native library:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:PYROSCOPE_AGENT_ENABLED = "true"
$env:PYROSCOPE_APPLICATION_NAME = "spring-modulith-kotlin"
$env:PYROSCOPE_SERVER_ADDRESS = "http://localhost:4040"
$env:PYROSCOPE_PROFILER_TYPE = "JFR"
.\gradlew.bat bootJar
java -javaagent:libs/agent-pyroscope-2.1.2.jar -jar build/libs/spring-modulith-kotlin-0.0.1-SNAPSHOT.jar
```

The agent offers two profiler types, selected with `PYROSCOPE_PROFILER_TYPE`:

- `ASYNC` (the agent's default) uses async-profiler. Its jar ships native libraries for linux-x64, linux-arm64 and macOS only, with **no Windows build**, so it cannot profile an app running directly on a Windows host. This is what containers use, and it produces the richer profile.
- `JFR` samples through JDK Flight Recorder and needs no native library, so it **does work on a Windows host**. Verified by running the app on Windows with `PYROSCOPE_PROFILER_TYPE=JFR` and confirming application frames arrived in Pyroscope.

WSL is a Linux environment and is not subject to the `ASYNC` limitation.

Attach the agent to the application JVM as shown above. `JAVA_TOOL_OPTIONS` applies to every JVM launched from that shell, including Gradle daemons.

To view profiles in Grafana:

1. Navigate to Explore.
2. Select Pyroscope as the datasource.
3. Pick a timerange and use the service selector to drill into `spring-modulith-kotlin` profiles.

## Security considerations

The application exposes `/actuator/prometheus` (and other actuator endpoints) without authentication because `management.endpoints.web.exposure.include=*` and `/actuator/**` is permitAll in both Spring Security filter chains. Adding the Prometheus registry surfaces application metrics alongside sensitive actuator data (e.g. `env` with `show-values=ALWAYS`). This configuration is appropriate for local development only. Before any non-local deployment, restrict actuator exposure and remove or protect the metrics endpoint; use environment-specific Spring profiles to separate local and production configurations.

Grafana runs with anonymous Admin access and no login form; Loki has authentication disabled; Tempo accepts unauthenticated OTLP; Pyroscope has authentication disabled. The whole stack is localhost-only by design and should never be exposed to untrusted networks without authentication and encryption layers.

Do not commit credentials to source control or embed them in Docker image layers.

The `Dockerfile` declares `ARG`/`ENV` pairs for `CLIENT_SECRET` and `APP_METHOD_API_TOKEN`. Values passed via `--build-arg` become `ENV` layers readable with `docker history`. Neither the CI workflow nor Compose passes build args today, so nothing is currently baked in, but supply these at runtime (`docker run -e` / Compose `environment`) rather than at build time. Removing the build-time `ARG`/`ENV` indirection is a pending follow-up.
