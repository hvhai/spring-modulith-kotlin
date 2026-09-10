---
title: "Replace Zipkin with Grafana LGTM observability stack"
description: "Remove Zipkin; emit metrics to Prometheus, traces to Tempo via OTLP, logs to Loki via loki4j, with Grafana as the single pre-provisioned UI. Also replaces the removed openjdk runtime base image."
status: completed
priority: P1
effort: 5h
branch: main
tags: [observability, tracing, metrics, logging, docker-compose, spring-boot]
created: 2026-09-10
---

# Replace Zipkin with Grafana LGTM Stack

Zipkin removed entirely. Three independent signal paths, separate containers:

| Signal | Path | Model |
|---|---|---|
| Metrics | Prometheus scrapes app `/actuator/prometheus` | pull |
| Traces | app -> Tempo `:4318` OTLP HTTP | push |
| Logs | app -> Loki `:3100` via loki4j logback appender | push |

Grafana `:3000` is the only UI, datasources pre-provisioned, traceId links Tempo <-> Loki
both directions.

## Locked decisions

- Boot 3.4.1 / Kotlin 1.9.25 / Java 21 — no version upgrades. All image tags pinned (Renovate).
- loki4j (not a log-tailing agent): the primary dev flow runs the app on the **host**, where a
  docker-log tailer would capture nothing.
- Port 9411 freed. New map: app 8080 (8088 in-container), Grafana 3000, Loki 3100,
  Tempo 3200/4317/4318, Prometheus 9090, MySQL 3316.
- Runtime base -> `eclipse-temurin:21.0.12_8-jre-noble`. **`openjdk:21-jdk-slim` was deleted from
  Docker Hub, so `docker build` is already broken cold-cache** — phase 03 is a P1 fix, not polish.

## Two run modes (both must work)

- **Mode A — app on host:** `docker compose up -d` (infra only) +
  `./gradlew bootRun --args='--spring.profiles.active=local'` — the `local` profile is what
  enables tracing and the Loki appender. Endpoints via `localhost`.
- **Mode B — app containerized:** `docker compose -f docker-compose-full.yml up`.
  Endpoints via compose service names. App listens on 8088 inside the network.

## Phases

| # | Phase | Status | Effort |
|---|---|---|---|
| 01 | [App dependencies & config](phase-01-app-observability-dependencies-and-config.md) | completed | 1h |
| 02 | [Observability stack config files](phase-02-observability-stack-config-files.md) | completed | 45m |
| 03 | [Docker image & Compose wiring](phase-03-docker-image-and-compose-wiring.md) | completed | 1.5h |
| 04 | [Documentation updates](phase-04-documentation-updates.md) | completed | 45m |
| 05 | [Verification & smoke tests](phase-05-verification-and-smoke-tests.md) | completed | 1h |

All phases completed. Implementation spans modes A (app on host) and B (fully containerized), with end-to-end verification across both.

## File ownership (no overlap between parallel phases)

- **01:** `build.gradle.kts`, `src/main/resources/application*.properties`,
  `logback-spring.xml`, `SpringModulithKotlinApplication.kt` — **02:** `docker/observability/*`
- **03:** `Dockerfile` + both compose files — **04:** `README.md`, `docs/*.md` — **05:** none

## Acceptance criteria

1. `grep -ri zipkin` over the repo returns 0 results.
2. `curl localhost:8080/actuator/prometheus` returns metrics; Prometheus target UP.
3. Calling an API produces a trace in Grafana -> Explore -> Tempo.
4. Logs for that request queryable in Loki; traceId matches the trace from (3).
5. `./gradlew check` passes.
6. `docker compose up -d` and `docker compose -f docker-compose-full.yml up` both come up.

Mapped to concrete commands in [phase 05](phase-05-verification-and-smoke-tests.md), which also
holds the per-phase rollback plan. AC #1 excludes `plans/`; AC #6 additionally proves the
container *serves traffic* on the new base image, not merely that it builds.

## Key research

Exact versions, config schemas, corrections to the sub-agent reports (authoritative): [`research/researcher-05-verified-findings-synthesis.md`](research/researcher-05-verified-findings-synthesis.md)

## Out of scope

Tempo metrics-generator / service graphs; alerting rules; persistent volumes; CI changes; security-config changes.

## Accepted follow-ups (deliberately not fixed in this plan)

The following issues were identified during implementation and verification. They are explicitly **accepted as-is** and documented for future roadmap work:

1. **Actuator metrics are publicly exposed** (`/actuator/prometheus` + related endpoints)
   - **Current state:** `management.endpoints.web.exposure.include=*` + `/actuator/**` is `permitAll` in both security filter chains (app config + MVC chain).
   - **Risk:** JVM, HTTP, and datasource metrics are published anonymously.
   - **Decision:** Acceptable for local development. Must be restricted before non-local deployment.
   - **Documented in:** `docs/deployment-guide.md` (security caveat section).
   - **Follow-up:** Restrict actuator exposure and add an auth'd actuator filter chain (future roadmap item).

2. **Dockerfile build args could bake secrets into image layers**
   - **Current state:** `Dockerfile:27-37` defines `ARG` variables for `CLIENT_SECRET` and `APP_METHOD_API_TOKEN` with `ENV` pass-through. If build args are supplied, they persist in image layer history.
   - **Reality:** No secrets are currently passed during CI or `docker compose build`. Neither `.github/workflows/` nor `docker-compose-full.yml` supply `--build-arg`.
   - **Decision:** Acceptable as long as build args are never used. Already documented as a security note.
   - **Documented in:** `docs/deployment-guide.md` (Docker build section).
   - **Follow-up:** Use BuildKit secrets or exclude args from image entirely (future roadmap item).

3. **Observability config duplicated across two compose files**
   - **Current state:** Identical loki/tempo/prometheus/grafana service blocks appear in both `docker-compose.yml` and `docker-compose-full.yml`.
   - **Why:** `docker-compose-full.yml` must stand alone (AC #6 requires it to work independently). Compose `include:` was rejected because it would also drag MySQL into `docker-compose-full.yml`, which currently intentionally has no database.
   - **Decision:** Duplication is deliberate and acceptable. Verified as byte-identical across both files.
   - **Follow-up:** Consider Compose `include:` (with MySQL scope decision) as a future refactor, not a bug.

4. **Five pre-existing integration tests fail with Testcontainers Docker environment issues**
   - **Current state:** AC #5 baseline: 15 tests total, 10 pass, 5 fail. All 5 failures are `initializationError` from Testcontainers attempting to find a valid Docker environment.
   - **Status:** Unchanged before/after the Zipkin→LGTM migration. Pre-existing.
   - **Also:** `WarehouseAllModuleTest` never appears in test output at all — worth a separate investigation.
   - **Decision:** Not a regression introduced by this plan. Inherited issue.
   - **Follow-up:** Diagnose and fix Testcontainers Docker environment detection (separate task, not blocking this migration).
