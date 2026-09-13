# Phase 04 — Documentation Updates

## Context Links

- [plan.md](plan.md)
- [phase-01](phase-01-app-observability-dependencies-and-config.md)
- [phase-02](phase-02-observability-stack-config-files.md)
- [phase-03](phase-03-docker-image-and-compose-wiring.md)

## Overview

- **Priority:** P2
- **Status:** completed
- **Effort:** 45m
- **Depends on:** phases 01–03 (all completed)
- Purge every Zipkin mention from `README.md` and `docs/`, describe the LGTM stack and the two run
  modes, and record the actuator-exposure risk.

## Key Insights

1. **Docs are the only remaining source of `zipkin` hits, so AC #1 lives or dies here.** Verified
   locations (12 hits across 6 files):

   | File | Lines |
   |---|---|
   | `README.md` | 12, 18, 46, 52 |
   | `docs/system-architecture.md` | 14, 40, 44 |
   | `docs/deployment-guide.md` | 8, 16, 53 |
   | `docs/codebase-summary.md` | 17, 37 |
   | `docs/project-overview-pdr.md` | 12, 34 |
   | `docs/project-roadmap.md` | 7 |

2. **The word "optional" must go with it.** Several docs describe tracing as "optional Zipkin".
   The replacement is not optional in the same sense — traces/logs require the stack to be running
   in Mode A/B, and the app still boots fine without it.
3. **Mode A needs the `local` profile spelled out**, otherwise a reader following the README gets
   no traces (default profile has `management.tracing.enabled=false`) and no Loki logs (appender
   is profile-gated).
4. `docs/project-roadmap.md:22` already lists "Add health/readiness checks and trace correlation
   guidance" as a medium-term priority — trace correlation is now delivered and should move to the
   baseline.

## Requirements

### Functional

- Zero occurrences of `zipkin` (any case) repo-wide.
- Document: service list + ports, both run modes, the Grafana correlation workflow, and the
  actuator exposure caveat.

### Non-functional

- Match existing doc tone: terse, table-driven, no marketing.
- Keep each doc's existing section structure; edit in place, do not add new files.

## Architecture

Documentation ownership map — one concern per file, no duplication:

| Doc | Gets |
|---|---|
| `README.md` | stack bullet, prerequisites, `docker compose up -d` + port table, both run modes |
| `docs/system-architecture.md` | operations layer, external services, deployment topology |
| `docs/deployment-guide.md` | service list, operational check URLs, correlation walkthrough, security caveat |
| `docs/codebase-summary.md` | compose file purpose, dependency list, `docker/observability/` entry |
| `docs/project-overview-pdr.md` | goal #4 wording, constraints |
| `docs/project-roadmap.md` | move trace correlation to baseline; add hardening follow-ups |

## Related Code Files

**Modify**

- `README.md`
- `docs/system-architecture.md`
- `docs/deployment-guide.md`
- `docs/codebase-summary.md`
- `docs/project-overview-pdr.md`
- `docs/project-roadmap.md`

**Create / Delete** — none.

## Implementation Steps

### 1. `README.md`

- Line 12: `OpenAPI/Swagger UI, Actuator and Zipkin tracing` ->
  `OpenAPI/Swagger UI, Actuator, and Grafana LGTM observability (Prometheus, Tempo, Loki)`.
- Line 18: `Docker Desktop for MySQL, Zipkin, or Testcontainers-based tests` ->
  `Docker Desktop for MySQL, the observability stack, or Testcontainers-based tests`.
- Lines 44–52 (`## Infrastructure`): replace with the service/port table and both run modes:

```markdown
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

### Run modes

- **App on the host** — `docker compose up -d` for infrastructure, then
  `./gradlew bootRun --args='--spring.profiles.active=local'`.
  The `local` profile enables tracing and the Loki log appender.
- **Everything containerized** — `docker compose -f docker-compose-full.yml up`.

The two modes publish the same host ports, so stop one before starting the other.
```

- Add a short line under the run modes pointing at
  `docs/deployment-guide.md` for the trace<->log correlation walkthrough.

### 2. `docs/system-architecture.md`

- Line 14: `... optional Zipkin exporter, and request tracing interceptor` ->
  `... OTLP span exporter, Prometheus meter registry, Loki log appender, and request tracing interceptor`.
- Line 40: replace the Zipkin bullet with three:

```markdown
- **Grafana LGTM stack:** Prometheus (9090) scrapes application metrics; Tempo (3200, OTLP
  4317/4318) collects traces pushed over OTLP HTTP; Loki (3100) receives logs pushed by the
  loki4j Logback appender. Grafana (3000) is the single UI with provisioned datasources.
```

- Line 44: replace the Zipkin sentence — Compose supplies MySQL and the observability stack; note
  the app listens on 8088 inside `docker-compose-full.yml` and is published as 8080.
- Add a short "Trace correlation" note to the data-flow section: traceId/spanId enter the MDC,
  are exported on the span **and** rendered into the log line, letting Grafana link both ways.

### 3. `docs/deployment-guide.md`

- Line 8: `Zipkin is optional.` -> `The Grafana LGTM observability stack (Prometheus, Tempo, Loki,
  Grafana) is optional for running the app, but required for metrics, traces, and logs.`
- Line 16: mention MySQL 3316 **and** the observability ports.
- Line 53 (`Operational checks`): replace the Zipkin UI bullet with:

```markdown
- Prometheus metrics endpoint: `/actuator/prometheus`
- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090` (Status -> Targets)
- Tempo: `http://localhost:3200`
- Loki: `http://localhost:3100`
```

- **Build and run with Docker** section (lines 25–32): this file documents the Docker build, so
  record the new runtime base image. Add after line 32:

```markdown
The runtime stage uses `eclipse-temurin:21.0.12_8-jre-noble`. A JRE is sufficient — the app runs
a Spring Boot fat jar and needs no compiler at runtime; the `jdk.jfr` module used by the request
tracing interceptor is present in Temurin JRE images. The previous `openjdk:21-jdk-slim` base was
removed from Docker Hub and no longer receives security updates.

The build stage (`gradle:8.11-jdk21-alpine`, musl) and the runtime stage (glibc) intentionally
use different C libraries. Only the compiled JAR crosses between stages, and bytecode is
libc-neutral, so the two do not need to match.
```

- Add a **Trace/log correlation** subsection: Grafana -> Explore -> Tempo -> find the trace ->
  "Logs for this span"; and Explore -> Loki -> `{app="spring-modulith-kotlin"}` -> expand a line ->
  "View Trace".
- Add a **Security caveat** subsection (see Security Considerations below).

### 4. `docs/codebase-summary.md`

- Line 17: `| docker-compose.yml | MySQL and Zipkin services |` ->
  `| docker-compose.yml | MySQL and the Grafana LGTM observability stack |`.
- Add a row: `| docker/observability/ | Prometheus, Tempo, and Grafana datasource provisioning |`.
- Line 37: `Micrometer/OpenTelemetry/Zipkin` ->
  `Micrometer tracing with the OpenTelemetry bridge and OTLP exporter, Micrometer Prometheus
  registry, loki4j Logback appender`.
- In "Application entry point and shared configuration", note that `logback-spring.xml` includes
  Boot's logging defaults and gates the Loki appender on the `local`/`docker` profiles.

### 5. `docs/project-overview-pdr.md`

- Line 12: goal 4 -> `Make local development observable with Actuator metrics, OTLP traces, and
  centralized logs through a Grafana LGTM stack.`
- Line 34: `MySQL and Zipkin are supplied by Docker Compose.` ->
  `MySQL and the observability stack (Prometheus, Tempo, Loki, Grafana) are supplied by Docker
  Compose.`

### 6. `docs/project-roadmap.md`

- Line 7: `... Actuator, and optional Zipkin tracing are integrated.` ->
  `... Actuator, and Grafana LGTM observability (Prometheus metrics, Tempo traces, Loki logs) are
  integrated.`
- Line 22: trace correlation is delivered — reword to remaining work.
- Add to medium-term priorities: restrict actuator exposure; Prometheus exemplars; treat Tempo/Loki
  major bumps as config-migration PRs.

### 7. Final sweep

```powershell
grep -ri zipkin .
```

Must return nothing (excluding `plans/`, which intentionally documents the migration).

## Todo List

- [x] `README.md` lines 12, 18, 44–52 updated
- [x] `docs/system-architecture.md` lines 14, 40, 44 + correlation note
- [x] `docs/deployment-guide.md` lines 8, 16, 53 + correlation + security caveat
- [x] `docs/deployment-guide.md` Docker section records the Temurin JRE base + libc rationale
- [x] `docs/codebase-summary.md` lines 17, 37 + `docker/observability/` row
- [x] `docs/project-overview-pdr.md` lines 12, 34
- [x] `docs/project-roadmap.md` lines 7, 22 + follow-ups
- [x] actuator-exposure risk documented
- [x] `grep -ri zipkin` clean outside `plans/`

## Success Criteria

- `grep -ri zipkin --exclude-dir=plans --exclude-dir=.git .` returns 0 results (AC #1).
- Both run modes are copy-pasteable from `README.md` with no missing step (specifically, the
  `--spring.profiles.active=local` flag is present).
- The port table in `README.md` matches `docker-compose.yml` exactly.
- The actuator-exposure caveat appears in `docs/deployment-guide.md`.
- `grep -rn openjdk docs/ README.md` returns nothing; the Temurin base image and the deliberate
  build/runtime libc mismatch are both documented in `docs/deployment-guide.md`.

## Risk Assessment

| # | Risk | L×I | Mitigation |
|---|---|---|---|
| R1 | A `zipkin` mention survives in prose (e.g. a sentence not on the listed lines) => AC #1 fails | Med×High | Case-insensitive repo-wide grep as the final step, not a per-file review |
| R2 | Docs describe ports that drift from compose | Med×Med | Success criterion diffs the README table against `docker-compose.yml` |
| R3 | README omits the `local` profile => a reader sees no traces and reports a bug | Med×Med | Explicit success criterion on the flag |
| R4 | The security caveat is written as a to-do and read as "already fixed" | Low×Med | Phrase it as a current, accepted limitation with a follow-up marker |
| R5 | `plans/` content trips the grep and is "fixed" by mistake | Med×Low | Exclusion documented in both the step and the criterion |

## Security Considerations

Document — do **not** fix — the following in `docs/deployment-guide.md`:

- `/actuator/**` is `permitAll` in both filter chains
  (`SpringModulithKotlinApplication.kt:89`, `:123`) and
  `management.endpoints.web.exposure.include=*` (`application.properties:45`). Adding the
  Prometheus registry therefore publishes `/actuator/prometheus` anonymously, alongside `env`
  with `show-values=ALWAYS`. **Accepted for local development; must be restricted before any
  non-local deployment.** Changing the security config is out of scope for this plan.
- Grafana runs with anonymous Admin and no login form; Loki has `auth_enabled: false`; Tempo
  accepts unauthenticated OTLP. The whole stack is localhost-only by design.
- Reiterate the existing rule: no credentials in source control.

## Next Steps

- Blocks phase 05 only insofar as AC #1 is verified there.
- Follow-up (not this plan): actuator hardening; binding observability ports to `127.0.0.1`.
