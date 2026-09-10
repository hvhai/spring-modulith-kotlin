# Phase 05 — Verification & Smoke Tests

## Context Links

- [plan.md](plan.md)
- [phase-01](phase-01-app-observability-dependencies-and-config.md) …
  [phase-04](phase-04-documentation-updates.md)
- [research/researcher-05-verified-findings-synthesis.md](research/researcher-05-verified-findings-synthesis.md)

## Overview

- **Priority:** P1 (gate — nothing ships until this passes)
- **Status:** completed
- **Effort:** 1h
- **Depends on:** phases 01–04 (all completed)
- Map each of the 6 acceptance criteria to a concrete command or UI step, run both modes, and hold
  the rollback plan.

## Key Insights

1. **AC #3 and #4 must be verified against the *same* traceId.** Verifying "a trace exists" and
   "logs exist" separately does not prove correlation. The procedure below captures one traceId
   from the response/console and uses it on both sides.
2. **Trace-by-ID is immediate; free-text Search is not.** Tempo indexes searchable traces
   asynchronously, so Search may lag 30–60s. Look the trace up **by traceId** first; only fall back
   to Search afterwards. A "missing" trace is usually indexing lag, not a broken exporter.
3. **One Prometheus target is always DOWN.** In Mode A `backend:8088` is DOWN; in Mode B
   `host.docker.internal:8080` is DOWN. This is expected — assert the *mode-appropriate* target is
   UP, not that all targets are UP.
4. **Modes are mutually exclusive.** Both compose files publish 3000/3100/3200/4317/4318/9090.
   Always `docker compose down` before switching.
5. **No container healthchecks exist by design** (distroless images). "Healthy" for AC #6 means:
   all containers `running`, and each readiness endpoint answers from the host.
6. **A successful `docker build` proves nothing about the new base image.** JDK->JRE regressions
   (a missing module such as `jdk.jfr`) only appear when the JVM starts and loads the class. AC #6
   therefore requires the container to answer HTTP, plus a log scan for `NoClassDefFoundError`.
   The build must also run with `--pull` — otherwise a cached copy of the deleted
   `openjdk:21-jdk-slim` can make a broken Dockerfile look fine locally while CI fails.

## Requirements

- Every acceptance criterion has a pass/fail command with an expected result.
- Both run modes exercised.
- No fake data, no mocks, no skipped assertions.

## Architecture

```
AC1 grep           ── static repo check
AC5 gradlew check  ── build gate            (no infra needed)
AC2 prometheus     ── metrics path
AC3 tempo          ── trace path      ┐
AC4 loki           ── log path        ├── same traceId ties 3+4 together
AC6 compose        ── both modes      ┘
```

## Related Code Files

None — this phase is read/verify only and must not modify any file. Any fix belongs in the owning
phase.

## Implementation Steps

### AC #5 — `./gradlew check` passes (run first, no infra needed)

```powershell
.\gradlew.bat clean check
```

**Pass:** build succeeds; test count matches the pre-change baseline; console shows **no** loki4j
connection errors (the appender is profile-gated off under `integration`).

### AC #1 — no Zipkin references

```powershell
grep -ri zipkin --exclude-dir=.git --exclude-dir=build --exclude-dir=plans .
```

**Pass:** no output. (`plans/` is excluded — it documents the migration by design.)

Same sweep for the removed base image (not part of AC #1, but the same class of leftover):

```powershell
grep -rn openjdk --exclude-dir=.git --exclude-dir=build --exclude-dir=plans .
```

**Pass:** no output. Note `Dockerfile:4` currently holds a commented-out
`#FROM openjdk:17-jdk-slim` line — remove it too rather than leaving a dead reference.

### AC #6a — Mode A infra comes up

```powershell
docker compose up -d
docker compose ps
```

**Pass:** 5 containers `running` — `my-sql`, `loki`, `tempo`, `prometheus`, `grafana`.
Then assert readiness **from the host** (the images have no shell for an internal check):

```powershell
curl -s -o /dev/null -w "loki  %{http_code}`n" http://localhost:3100/ready
curl -s -o /dev/null -w "tempo %{http_code}`n" http://localhost:3200/ready
curl -s -o /dev/null -w "prom  %{http_code}`n" http://localhost:9090/-/ready
curl -s -o /dev/null -w "graf  %{http_code}`n" http://localhost:3000/api/health
```

**Pass:** all `200`. Loki/Tempo may briefly return 503 while starting — retry for ~30s.

### Start the app (Mode A)

```powershell
$env:MYSQL_ROOT_PASSWORD="pw"    # matches application-local.properties
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

**Pass:** startup dump prints
`management.otlp.tracing.endpoint  = http://127.0.0.1:4318/v1/traces` and
`management.tracing.enabled  = true`; console log lines show `[spring-modulith-kotlin,,]` or a
populated traceId.

### AC #2 — metrics endpoint + Prometheus target UP

```powershell
curl -s http://localhost:8080/actuator/prometheus | Select-Object -First 15
```

**Pass:** Prometheus text exposition output (e.g. `jvm_memory_used_bytes`, `http_server_requests_*`).

Then Prometheus -> http://localhost:9090/targets:

**Pass:** job `spring-modulith-kotlin`, endpoint `host.docker.internal:8080` (label
`run_mode="host"`) is **UP**. `backend:8088` is DOWN — expected in this mode.

Equivalent CLI check:

```powershell
curl -s "http://localhost:9090/api/v1/targets?state=active"
```

### AC #3 — trace visible in Tempo

Generate traffic against an endpoint that exists in this app:

```powershell
curl -i http://localhost:8080/api/todos
```

Capture the traceId from the console line for that request — the segment is
`[spring-modulith-kotlin,<traceId>,<spanId>]`.

Grafana -> http://localhost:3000 -> Explore -> datasource **Tempo** -> query type **TraceQL** ->
paste the traceId into the *TraceID* search.

**Pass:** the trace opens with at least one span named after the HTTP route.
If Search shows nothing, wait 60s and retry — but TraceID lookup should be immediate.

Sanity check that spans are actually arriving:

```powershell
docker compose logs tempo --tail 50
```

### AC #4 — logs in Loki with the matching traceId

Grafana -> Explore -> datasource **Loki** -> query:

```logql
{app="spring-modulith-kotlin"} |= "<traceId from AC#3>"
```

**Pass:** at least one log line returns, and its bracket segment contains the *same* traceId.

Then verify correlation **both directions**:

- **Loki -> Tempo:** expand a returned log line; a `TraceID` derived field with a **View Trace**
  button appears and opens the same trace. If the button is missing, the `matcherRegex` did not
  match. If it opens an empty trace, the `$$` escaping is wrong.
- **Tempo -> Loki:** open the trace from AC #3, select a span, click **Logs for this span**.
  **Pass:** the same log lines appear.

CLI cross-check:

```powershell
curl -s -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode 'query={app="spring-modulith-kotlin"}' --data-urlencode "limit=5"
```

### AC #6b — Mode B comes up, on the new base image

**Build from a cold cache first.** `--pull` is mandatory: without it the build can succeed against
a locally cached copy of the deleted `openjdk:21-jdk-slim` and hide the whole problem.

```powershell
docker image rm spring-modulith-kotlin:latest modulith-project:latest 2>$null
docker build . --pull --tag modulith-project:latest --platform=linux/amd64
```

**Pass:** build completes and pulls `eclipse-temurin:21.0.12_8-jre-noble`.

Confirm the JVM inside the produced image, and that JFR is available — a build-only check would
not catch a missing module:

```powershell
docker run --rm modulith-project:latest java -version
docker run --rm modulith-project:latest java --list-modules | Select-String jfr
```

**Pass:** `Temurin-21.0.12+8`; both `jdk.jfr` and `jdk.management.jfr` listed.

Then bring up the stack:

```powershell
docker compose down
docker compose -f docker-compose-full.yml up --build
```

**Pass:** 5 containers running (`backend`, `loki`, `tempo`, `prometheus`, `grafana`); backend logs
show profile `docker` active.

**The container must actually serve traffic — not merely build and stay up.** A missing runtime
module (e.g. `jdk.jfr` behind `TraceHandler`) surfaces only once the app boots and handles a
request:

```powershell
curl -s -o /dev/null -w "root     %{http_code}`n" http://localhost:8080/
curl -s -o /dev/null -w "health   %{http_code}`n" http://localhost:8080/actuator/health
curl -s -o /dev/null -w "api      %{http_code}`n" http://localhost:8080/api/todos
docker compose -f docker-compose-full.yml logs backend --tail 100
```

**Pass:** HTTP responses (not connection-refused), `actuator/health` returns 200, and the backend
log contains **no** `NoClassDefFoundError`, `ClassNotFoundException`, or
`UnsatisfiedLinkError`. `/api/todos` exercises `TraceHandler`, which is the JFR code path.

Repeat AC #2/#3/#4 in this mode:

- `curl http://localhost:8080/actuator/prometheus` (published 8080 -> container 8088).
- Prometheus `/targets`: `backend:8088` (`run_mode="container"`) **UP**;
  `host.docker.internal:8080` DOWN — expected.
- Trace + log correlation exactly as above.

Finally:

```powershell
docker compose -f docker-compose-full.yml down
```

## Todo List

- [x] AC5 `.\gradlew.bat clean check` green, baseline test count
- [x] AC1 `grep -ri zipkin` clean
- [x] AC6a `docker compose up -d` -> 5 running + 4 readiness 200s
- [x] Mode A app boots with `local` profile, startup dump shows OTLP endpoint
- [x] AC2 `/actuator/prometheus` non-empty; `run_mode="host"` target UP
- [x] AC3 trace found in Tempo by traceId
- [x] AC4 logs found in Loki with same traceId
- [x] AC4 Loki -> Tempo "View Trace" works
- [x] AC4 Tempo -> Loki "Logs for this span" works
- [x] AC6b `docker build --pull` succeeds on the new Temurin base (old image removed first)
- [x] AC6b image reports `Temurin-21.0.12+8`; `jdk.jfr` + `jdk.management.jfr` present
- [x] AC6b container **serves traffic** — `/actuator/health` 200, `/api/todos` responds
- [x] AC6b backend logs free of `NoClassDefFoundError` / `ClassNotFoundException`
- [x] AC6b `docker-compose-full.yml` up -> 5 running; AC2/3/4 repeated
- [x] both compose stacks torn down; observability blocks byte-identical across the two files

## Success Criteria

All six acceptance criteria pass, with #3 and #4 demonstrated on the **same** traceId and
correlation working in **both** directions.

## Risk Assessment

| # | Risk | L×I | Mitigation |
|---|---|---|---|
| R1 | Trace absent from Tempo Search, wrongly diagnosed as a broken exporter | High×Low | Look up by TraceID first; check `docker compose logs tempo`; allow 60s for Search |
| R2 | Correlation link renders but opens nothing — `$` escaping | Med×High | Distinct symptom documented (button present vs. empty trace); fix belongs to phase 02 |
| R3 | Both stacks started at once => port conflicts misread as config errors | Med×Med | `docker compose down` between modes, stated as a step |
| R4 | Mode A run without the `local` profile => tracing off, no Loki logs, AC #3/#4 "fail" | High×Med | Explicit `--spring.profiles.active=local` in the command; startup-dump assertion catches it |
| R5 | `MYSQL_ROOT_PASSWORD` unset => MySQL container unhealthy, Mode A app fails to start | Med×Med | Exported in the step; matches `application-local.properties` credentials |
| R6 | Verifier edits files to make a check pass, blurring phase ownership | Low×Med | This phase is read-only; fixes go back to the owning phase and re-run |
| R7 | `/api/todos` requires auth or does not exist => no trace generated | Med×Med | `/actuator/**` and `/api/**` are `permitAll`; if it 404s, any actuator call also produces a trace |
| R8 | `docker build` passes off a warm cache of the deleted `openjdk` base, hiding the breakage until CI | High×High | Old images removed and `--pull` forced before building |
| R9 | Image builds and container stays "up" while the app died at startup (JDK->JRE module regression) | Low×High | Verification requires an HTTP 200 from `/actuator/health` **and** a clean log scan, not just `docker ps` |

## Rollback Plan

Each phase reverts independently; there is no data migration and no persistent state.

| Phase | Rollback | Cascade risk |
|---|---|---|
| 01 | `git checkout -- build.gradle.kts src/main/resources src/main/kotlin/.../SpringModulithKotlinApplication.kt` and delete `logback-spring.xml` | None. Deleting `logback-spring.xml` restores Boot's built-in logging. |
| 02 | `git rm -r docker/observability` | Breaks 03's bind mounts — revert 03 too, or Tempo/Prometheus start with defaults. |
| 03 | `git checkout -- docker-compose.yml docker-compose-full.yml Dockerfile` | Run `docker compose down` first to release ports. **Reverting `Dockerfile` restores a base image that no longer exists** — the build will only work off a warm cache. Prefer fixing forward. |
| 04 | `git checkout -- README.md docs/` | None (docs only). |
| All | Revert the whole branch. Zipkin returns as it was; port 9411 is re-published. | None — no volumes, no schema changes, no external state. |

Partial-state note: 01 without 03 is safe — the app simply cannot reach Tempo/Loki and retries in
the background. 03 without 01 is also safe — the stack runs with no data flowing into it.

## Security Considerations

- Smoke testing exposes `/actuator/env` with `show-values=ALWAYS`; run these checks only on a
  local machine and do not paste `/actuator/env` output into tickets or reports.
- Grafana anonymous Admin is active during verification — do not port-forward or tunnel 3000.
- Do not commit `MYSQL_ROOT_PASSWORD` or any exported env var; use a shell session variable only.

## Next Steps

On green: hand back for review/commit. Deferred follow-ups recorded in
`docs/project-roadmap.md` — restrict actuator exposure, bind observability ports to `127.0.0.1`,
Prometheus exemplars, and treating Tempo/Loki major bumps as config-migration PRs.
