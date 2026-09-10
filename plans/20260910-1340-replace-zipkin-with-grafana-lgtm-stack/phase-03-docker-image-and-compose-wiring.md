# Phase 03 — Docker Image & Compose Wiring

## Context Links

- [plan.md](plan.md)
- [phase-01](phase-01-app-observability-dependencies-and-config.md) — env var names
- [phase-02](phase-02-observability-stack-config-files.md) — mounted config files
- [research/researcher-05-verified-findings-synthesis.md](research/researcher-05-verified-findings-synthesis.md)
  — §11 covers the base-image research

## Overview

- **Priority:** P1 (blocks 04, 05; also fixes an existing broken build)
- **Status:** completed
- **Effort:** 1.5h
- **Depends on:** phase 01 + phase 02 (both completed)
- Replace the deleted `openjdk:21-jdk-slim` runtime base image; delete both `zipkin-all-in-one`
  services; add Grafana + Tempo + Loki + Prometheus to both compose files; repoint the `backend`
  service env to OTLP and Loki.

## Key Insights

0. **`openjdk:21-jdk-slim` no longer exists.** `Dockerfile:20` references a tag that has been
   removed from Docker Hub (`/tags/21-jdk-slim` -> **404**, `docker manifest inspect` ->
   not pullable). The repo's `docker build` therefore already fails on any machine without a warm
   cache — including `.github/workflows/ci.yml:66` and the `build:` in `docker-compose-full.yml`.
   This is a **pre-existing P1 breakage** folded into this phase, not a cosmetic upgrade. The
   build stage `gradle:8.11-jdk21-alpine` is unaffected (verified pullable).
1. **The observability block is duplicated across both compose files, on purpose.** AC #6 requires
   `docker compose -f docker-compose-full.yml up` to work standalone, so it cannot rely on
   `docker-compose.yml`. The removed `zipkin-all-in-one` service was already duplicated the same
   way — this preserves the repo's existing pattern. (A DRY alternative via Compose `include:` is
   listed under Next Steps but is rejected here because it would also drag MySQL into
   `docker-compose-full.yml`, which currently has no database service.)
2. **No `healthcheck:` blocks are added — deliberately.** `grafana/loki:3.7.7` and
   `grafana/tempo:3.0.3` are built `FROM gcr.io/distroless/static*`: no shell, no `curl`, no
   `wget`. Any exec-based healthcheck is guaranteed to fail and would make `docker compose up`
   report unhealthy containers. Readiness is asserted from the host in phase 05 instead.
3. **Tempo needs `-target=all`.** The single-binary launch command is
   `["-target=all", "-config.file=/etc/tempo.yaml"]`. Omitting `-target=all` is a common cause of
   a container that starts and then does nothing useful.
4. **Tempo needs no chown sidecar.** Upstream examples ship a busybox `init` service only because
   they bind-mount `./tempo-data`. This plan mounts no volume, and the image already creates
   `/var/tempo` owned by `10001:10001` with mode `0700`.
5. **Prometheus needs `extra_hosts`** to reach the host-mode app — reusing exactly the
   `extra_hosts: ['host.docker.internal:host-gateway']` line the old zipkin service carried
   (`docker-compose.yml:19`).
6. **`SPRING_PROFILES_ACTIVE=docker` is a pure switch.** There is no `application-docker.properties`,
   so activating it changes no property — it exists solely to turn on the profile-gated Loki
   appender from phase 01.
7. **A JRE is sufficient, and JFR was the thing that could have broken it.**
   `SpringModulithKotlinApplication.kt:11-13` imports `jdk.jfr.*` and `TraceHandler.HTTPEvent`
   (`:221`) extends `jdk.jfr.Event`; the interceptor is registered at `:170`, so a missing
   `jdk.jfr` module fails at **startup**, not at build. Verified empirically:
   `docker run --rm eclipse-temurin:21-jre-noble java --list-modules` lists `jdk.jfr@21.0.12` and
   `jdk.management.jfr@21.0.12`. A full audit also confirmed `java.desktop` (Spring's
   `java.beans`), `jdk.unsupported` (`sun.misc.Unsafe`), `java.sql`, `java.instrument`,
   `java.management`, `jdk.crypto.ec` and `java.net.http` are present. **No JDK fallback needed.**

## Requirements

### Functional

- Runtime stage uses a maintained, pullable, fully-pinned Temurin **JRE** 21 image.
- Multi-stage layout, ARG/ENV pass-through, `EXPOSE 8080` and the `ENTRYPOINT` stay intact.
- `docker-compose.yml`: MySQL + Grafana + Tempo + Loki + Prometheus. **No app service** (unchanged
  premise — infra only).
- `docker-compose-full.yml`: `backend` + Grafana + Tempo + Loki + Prometheus.
- All images pinned. Zipkin gone from both files. Port 9411 no longer published.

### Non-functional

- Keep the existing `spring-modulith-kotlin-network` and `platform: linux/amd64` conventions.
- No credentials in either file; keep `${VAR}` env indirection as-is.
- No persistent volumes for observability containers.
- Runtime base stays **glibc** to keep the swap behaviour-neutral vs the outgoing Debian image.

## Architecture

Port map (no conflicts):

| Service | Host port | Container | Notes |
|---|---|---|---|
| app (Mode A, host) | 8080 | — | `bootRun` |
| `backend` (Mode B) | 8080 | 8088 | `SERVER_PORT=8088` |
| grafana | 3000 | 3000 | single UI |
| loki | 3100 | 3100 | push + query |
| tempo | 3200 | 3200 | API/UI |
| tempo OTLP | 4317 / 4318 | 4317 / 4318 | grpc / http |
| prometheus | 9090 | 9090 | |
| my-sql | 3316 | 3306 | unchanged |
| ~~zipkin~~ | ~~9411~~ | — | **freed** |

## Related Code Files

**Modify**

- `Dockerfile` — line 20 only (runtime `FROM`)
- `docker-compose.yml` — delete lines 16–23 (`zipkin-all-in-one`), add 4 services
- `docker-compose-full.yml` — delete lines 31–38 (`zipkin-all-in-one`), rewrite `backend` env
  (lines 16–18) and `depends_on` (lines 27–28), add 4 services

**Create / Delete** — none.

No other phase touches `Dockerfile` — confirmed: phase 01 owns `build.gradle.kts` +
`src/main/**`, phase 02 owns `docker/observability/**`, phase 04 owns `README.md` + `docs/**`,
phase 05 writes nothing.

## Implementation Steps

### 0. `Dockerfile` — runtime base image

Two edits, both `openjdk` removals.

**a. Line 20** — the live runtime base:

```dockerfile
FROM openjdk:21-jdk-slim
```

becomes:

```dockerfile
FROM eclipse-temurin:21.0.12_8-jre-noble
```

**b. Line 4** — delete the dead commented-out alternative, which points at a base image from the
same removed repository and would otherwise survive the `grep -rn openjdk` sweep in phase 05:

```dockerfile
#FROM openjdk:17-jdk-slim as build
```

Everything else is unchanged — the `gradle:8.11-jdk21-alpine` build stage (line 3),
`WORKDIR /app`, the `COPY --from=build` of the fat jar (line 24), the four `ARG`/`ENV` pairs
(lines 27–37), `EXPOSE 8080` (line 39), and
`ENTRYPOINT ["java","-jar","app.jar"]` (line 42).

Why this tag:

- **Pullable and current.** `docker manifest inspect eclipse-temurin:21.0.12_8-jre-noble` succeeds;
  pushed 2026-09-09; image reports `Temurin-21.0.12+8 (build 21.0.12+8-LTS)`.
- **Fully pinned** (`21.0.12_8`) rather than floating `21-jre-noble`, so Renovate proposes explicit
  patch bumps. Temurin tags carry **no** `v` prefix — verified against the registry, not assumed.
- **JRE, not JDK.** Sufficient for `java -jar`; JFR verified present (Key Insight 7).
- **noble (glibc), not alpine (musl).** The outgoing `openjdk:21-jdk-slim` was Debian/glibc, so
  noble is the minimum-change swap — no DNS-resolver, locale/timezone or default-stack-size
  differences. Alpine would save ~24 MB (71 vs 95 MB compressed) but changes libc and drops
  ppc64le/s390x/riscv64. Not worth it here.

> **Do not "align" the two stages' libc.** The build stage is alpine/musl and the runtime stage
> is glibc. This is intentional and correct: only `build/libs/*.jar` crosses the stage boundary
> (line 24), and a JAR is libc- and architecture-neutral bytecode. There is no JNI, no
> native-image, and no compiled artifact carried over. A future reader should not "fix" this.

### 1. Shared observability block (identical in both files)

```yaml
  loki:
    image: grafana/loki:3.7.7
    platform: linux/amd64
    command: [ "-config.file=/etc/loki/local-config.yaml" ]
    ports:
      - "3100:3100"
    networks:
      - spring-modulith-kotlin-network

  tempo:
    image: grafana/tempo:3.0.3
    platform: linux/amd64
    command: [ "-target=all", "-config.file=/etc/tempo.yaml" ]
    volumes:
      - ./docker/observability/tempo.yaml:/etc/tempo.yaml:ro
    ports:
      - "3200:3200"   # tempo API/UI
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
    networks:
      - spring-modulith-kotlin-network

  prometheus:
    image: prom/prometheus:v3.14.0
    platform: linux/amd64
    command:
      - --config.file=/etc/prometheus/prometheus.yml
    extra_hosts: [ 'host.docker.internal:host-gateway' ]
    volumes:
      - ./docker/observability/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    ports:
      - "9090:9090"
    networks:
      - spring-modulith-kotlin-network

  grafana:
    image: grafana/grafana:13.2.1
    platform: linux/amd64
    environment:
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Admin
      - GF_AUTH_DISABLE_LOGIN_FORM=true
    volumes:
      - ./docker/observability/grafana-datasources.yaml:/etc/grafana/provisioning/datasources/datasources.yaml:ro
    ports:
      - "3000:3000"
    depends_on:
      - prometheus
      - tempo
      - loki
    networks:
      - spring-modulith-kotlin-network
```

`GF_AUTH_ANONYMOUS_ORG_ROLE=Admin` matches the upstream Grafana example and guarantees Explore
access without a login form. No credentials are stored.

Bind-mount paths are relative to the compose file, which sits at the repo root in both cases, so
`./docker/observability/...` is correct for both files.

### 2. `docker-compose.yml`

- Keep `my-sql` (lines 2–14) unchanged.
- **Delete** the whole `zipkin-all-in-one` block (lines 16–23).
- Append the shared observability block above.
- Keep the trailing `networks: spring-modulith-kotlin-network:` declaration.

### 3. `docker-compose-full.yml`

- **Delete** the whole `zipkin-all-in-one` block (lines 31–38).
- In `backend.environment`, replace lines 16–18 with:

```yaml
      - SPRING_PROFILES_ACTIVE=docker
      - MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
      - MANAGEMENT_TRACING_ENABLED=true
      - MANAGEMENT_OTLP_TRACING_ENDPOINT=http://tempo:4318/v1/traces
      - MANAGEMENT_OTLP_TRACING_TRANSPORT=http
      - LOKI_URL=http://loki:3100
```

- Replace `depends_on` (lines 27–28) with:

```yaml
    depends_on:
      - tempo
      - loki
```

- Append the shared observability block.
- Leave `ports: 8080:8088`, `SERVER_PORT=8088`, the `deploy.resources` limits and the OAuth2 env
  vars untouched.

### 4. Validate

```powershell
docker compose -f docker-compose.yml config
docker compose -f docker-compose-full.yml config
docker build . --tag spring-modulith-kotlin:latest --platform=linux/amd64
```

The build is only half the check — phase 05 also starts the container, because a missing runtime
module surfaces at startup, not at build time.

## Todo List

- [x] `Dockerfile:20`: `openjdk:21-jdk-slim` -> `eclipse-temurin:21.0.12_8-jre-noble`
- [x] `Dockerfile:4`: delete the dead `#FROM openjdk:17-jdk-slim as build` comment
- [x] `Dockerfile`: build stage, ARG/ENV block, `EXPOSE`, `ENTRYPOINT` untouched
- [x] `docker build` succeeds from a cold cache (`--pull`)
- [x] `docker-compose.yml`: delete `zipkin-all-in-one`
- [x] `docker-compose.yml`: add loki / tempo / prometheus / grafana
- [x] `docker-compose-full.yml`: delete `zipkin-all-in-one`
- [x] `docker-compose-full.yml`: backend env -> OTLP + `LOKI_URL` + `SPRING_PROFILES_ACTIVE=docker`
- [x] `docker-compose-full.yml`: `depends_on` -> tempo, loki
- [x] `docker-compose-full.yml`: add loki / tempo / prometheus / grafana
- [x] `extra_hosts` present on prometheus in **both** files
- [x] no `9411` anywhere; all image tags pinned
- [x] `docker compose config` clean for both files

## Success Criteria

- `grep -ri zipkin Dockerfile docker-compose.yml docker-compose-full.yml` returns nothing.
- `grep -n 9411 docker-compose*.yml` returns nothing.
- `grep -n openjdk Dockerfile` returns nothing.
- `docker compose config` succeeds for both files.
- No image reference lacks an explicit tag (`grep -n 'image:\|^FROM' docker-compose*.yml Dockerfile`
  shows a `:tag` on every line).
- `docker build . --pull` succeeds with no local cache of the old base image.
- `docker compose up -d` brings up 5 containers; `docker compose -f docker-compose-full.yml up`
  brings up 5 containers.
- Container startup is proven in phase 05, not here.

## Risk Assessment

| # | Risk | L×I | Mitigation |
|---|---|---|---|
| R1 | Both compose files run simultaneously => host port collisions on 3000/3100/3200/4317/4318/9090 | High×Med | Documented in phase 04 as mutually exclusive; `docker compose down` before switching modes |
| R2 | Adding `healthcheck` blocks to distroless Tempo/Loki => permanently unhealthy containers, AC #6 appears to fail | Med×High | Explicit decision: **no** healthchecks; readiness asserted from host in phase 05 |
| R3 | Duplicated observability block drifts between the two files over time | Med×Low | Blocks must stay byte-identical; phase 05 diffs them |
| R4 | `SPRING_PROFILES_ACTIVE=docker` accidentally masks default-profile config | Low×Med | No `application-docker.properties` exists — verified; the profile is a pure switch |
| R5 | Bind-mount path wrong => Tempo/Prometheus start with default config and silently misbehave | Med×Med | `docker compose config` shows resolved paths; phase 05 asserts the OTLP listeners and the scrape target |
| R6 | Tempo launched without `-target=all` | Low×High | Command pinned in step 1 |
| R7 | `backend` starts before Loki/Tempo are ready => first pushes fail | High×Low | Exporters retry; loki4j is async and non-blocking. `depends_on` only orders startup |
| R8 | Image builds but the container dies at startup on a missing JVM module (classic JDK->JRE regression, e.g. `jdk.jfr` for `HTTPEvent`) | Low×High | Module set audited empirically against the actual image (Key Insight 7); phase 05 asserts the container **serves traffic**, not just that it builds |
| R9 | Someone later "harmonises" the alpine build stage with the glibc runtime stage, or swaps runtime to alpine for size | Med×Med | Rationale recorded inline in step 0 as a blockquote and in research §11.4 |
| R10 | Fully-pinned `21.0.12_8` goes stale / a CVE lands before the next manual bump | Med×Med | Renovate tracks the pin and opens patch PRs; JRE (not JDK) already reduces attack surface |
| R11 | `docker build` validated only against a warm local cache, hiding the removed-base-image failure | Med×High | Success criterion requires `--pull` |

## Security Considerations

- Moving from a **JDK** to a **JRE** base removes the compiler/tooling chain from the runtime
  image, shrinking the attack surface. Image also drops from an unmaintained, deleted base to a
  currently-patched Temurin LTS build (21.0.12+8).
- `openjdk:21-jdk-slim` was not merely deprecated but **removed**, so it receives no security
  updates whatsoever — anyone still running a cached copy is on an unpatched JVM.
- The `ARG`/`ENV` block (`Dockerfile:27-37`) bakes `CLIENT_SECRET` / `APP_METHOD_API_TOKEN` into
  image layers if build args are supplied. Pre-existing, unchanged by this phase, and **not** to
  be fixed here — record it as a follow-up (build args are visible in image history).
- Grafana runs with **anonymous Admin** and the login form disabled. Acceptable for a local dev
  stack, unacceptable anywhere reachable. Phase 04 documents this explicitly.
- Publishing 3000/3100/3200/4317/4318/9090 binds to all host interfaces by default. On an
  untrusted network prefix each with `127.0.0.1:` — noted as a follow-up, not applied here to
  keep parity with the existing MySQL port style.
- Tempo's OTLP receivers accept unauthenticated spans from anyone who can reach 4317/4318.
- No credentials added to either compose file; existing `${VAR}` indirection preserved.

## Next Steps

- Blocks phase 04 (docs describe the final service list/ports **and the new base image**) and
  phase 05 (which proves the container actually serves traffic on the new base).
- Follow-up (not this plan): deduplicate the observability block via Compose `include:`, which
  would also require deciding whether `docker-compose-full.yml` should gain a MySQL service;
  stop passing secrets as Docker build args.
