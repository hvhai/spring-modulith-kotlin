# Deployment Guide

## Requirements

- Java 21 for direct JVM execution, or Docker for container deployment.
- Auth0/OIDC application credentials for authenticated flows.
- H2 for the default in-memory setup, or MySQL for a persistent deployment.
- The Grafana LGTM observability stack (Prometheus, Tempo, Loki, Grafana) is optional for running the app, but required for metrics, traces, and logs.

## Local supporting services

```shell
docker compose up -d
```

This starts MySQL on host port `3316`, Prometheus on `9090`, Tempo on `3200`, Loki on `3100`, and Grafana on `3000`. Supply `MYSQL_ROOT_PASSWORD` in the environment before starting Compose.

## Build and run with Gradle

```shell
./gradlew clean build
./gradlew bootRun
```

## Build and run with Docker

```shell
docker build . --tag spring-modulith-kotlin:latest --platform=linux/amd64
docker run --rm -p 8080:8080 spring-modulith-kotlin:latest
```

The image exposes port 8080 and starts the packaged Spring Boot JAR. The Dockerfile skips tests during image construction, so run `./gradlew clean check` as a separate release gate.

The runtime stage uses `eclipse-temurin:21.0.12_8-jre-noble`. A JRE is sufficient — the app runs a Spring Boot fat jar and needs no compiler at runtime; the `jdk.jfr` module used by the request tracing interceptor is present in Temurin JRE images. The previous `openjdk:21-jdk-slim` base was removed from Docker Hub and no longer receives security updates.

The build stage (`gradle:8.11-jdk21-alpine`, musl) and the runtime stage (glibc) intentionally use different C libraries. Only the compiled JAR crosses between stages, and bytecode is libc-neutral, so the two do not need to match.

## Configuration

Relevant environment variables include:

- `CLIENT_ID`
- `CLIENT_SECRET`
- `APP_H2_PASS`
- `APP_METHOD_API_TOKEN`
- `DOMAIN`
- `MYSQL_ROOT_PASSWORD` for Compose

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

## Security considerations

The application exposes `/actuator/prometheus` (and other actuator endpoints) without authentication because `management.endpoints.web.exposure.include=*` and `/actuator/**` is permitAll in both Spring Security filter chains. Adding the Prometheus registry surfaces application metrics alongside sensitive actuator data (e.g. `env` with `show-values=ALWAYS`). This configuration is appropriate for local development only. Before any non-local deployment, restrict actuator exposure and remove or protect the metrics endpoint; use environment-specific Spring profiles to separate local and production configurations.

Grafana runs with anonymous Admin access and no login form; Loki has authentication disabled; Tempo accepts unauthenticated OTLP. The whole stack is localhost-only by design and should never be exposed to untrusted networks without authentication and encryption layers.

Do not commit credentials to source control or embed them in Docker image layers.

The `Dockerfile` declares `ARG`/`ENV` pairs for `CLIENT_SECRET` and `APP_METHOD_API_TOKEN`. Values passed via `--build-arg` become `ENV` layers readable with `docker history`. Neither the CI workflow nor Compose passes build args today, so nothing is currently baked in, but supply these at runtime (`docker run -e` / Compose `environment`) rather than at build time. Removing the build-time `ARG`/`ENV` indirection is a pending follow-up.
