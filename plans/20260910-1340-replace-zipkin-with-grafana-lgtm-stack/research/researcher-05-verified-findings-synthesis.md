# Verified Findings Synthesis (authoritative)

Date: 2026-09-10. This file OVERRIDES researcher-01..04 where they conflict.
Every value below was re-verified directly against Maven Central metadata, Docker Hub tag API,
raw upstream config files at the exact pinned git tag, or Grafana docs. Corrections to the
sub-agent reports are called out explicitly.

---

## 1. Image tags (verified via Docker Hub `/v2/repositories/{repo}/tags/{tag}` -> HTTP 200)

| Image | PINNED TAG | Verified |
|---|---|---|
| Grafana | `grafana/grafana:13.2.1` | 200; pushed 2026-09-01; highest semver present |
| Tempo | `grafana/tempo:3.0.3` | 200; GitHub `releases/latest` = `v3.0.3`, published 2026-08-13 |
| Loki | `grafana/loki:3.7.7` | 200 |
| Prometheus | `prom/prometheus:v3.14.0` | 200; pushed 2026-08-18; `latest`+`v3` resolve to this batch |

### CORRECTION - tag prefixes

researcher-03 wrote `grafana/tempo:v2.10.8` and `grafana/loki:v3.7.7`. **Both are wrong.**
Grafana's Tempo/Loki images use **no `v` prefix**. Probe results:

```
grafana/tempo:2.10.8  -> 200      grafana/tempo:v2.10.8  -> 404
grafana/loki:3.7.7    -> 200      grafana/loki:v3.7.7    -> 404
```

Prometheus **does** use the `v` prefix (`prom/prometheus:v3.14.0`). Grafana does not (`13.2.1`).

### CORRECTION - Tempo major version

researcher-03 recommended Tempo 2.10.8 claiming v3 has breaking changes needing migration.
Re-checked: `v3.0.3` is the current latest release and its official **single-binary** example
config is *simpler* than 2.x (no `ingester:` / `compactor:` blocks required). Pinning 3.0.3
avoids an immediate Renovate major-bump PR that would break a 2.x config. Source of truth:
`example/docker-compose/single-binary/tempo.yaml` @ tag `v3.0.3`.

`prom/prometheus:v3.13.3` also exists (2026-09-07, backport line). `v3.14.0` chosen because
`latest`/`v3` point at it.

---

## 2. loki4j appender

**Coordinate:** `com.github.loki4j:loki-logback-appender:2.1.0`

Verified via authoritative `maven-metadata.xml` at repo1.maven.org:
`<latest>2.1.0</latest>`, `<release>2.1.0</release>`, `lastUpdated 20260730192920`.

### CORRECTION - release date

researcher-01 stated "v2.1.0, July 30 **2024**". The maven-metadata `lastUpdated` is
`20260730192920` = **2026-07-30**. Version number right, year wrong by two. (The
`search.maven.org` solr index is stale and only lists up to 2.0.0; `maven-metadata.xml` is
the authoritative source.)

- Requires Java 17+. Java 21 OK. Logback 1.5.x (Boot 3.4.1) OK.
- Plain artifact, no JDK classifier.
- No extra runtime dependency: default sender is the JDK `HttpClient`.

### CORRECTION - XML schema

researcher-01's sample contained **two sibling `<http>` blocks**. That is invalid. Verified
schema: `<http>` appears **once**, and the retry knobs live *inside* it.

Valid v2.x `<appender>` children: `<labels>`, `<structuredMetadata>`, `<message>`, `<http>`,
`<batch>`, `<verbose>`.

`<http>` children include: `<url>`, `<connectionTimeoutMs>`, `<requestTimeoutMs>`,
`<maxRetries>`, `<minRetryBackoffMs>`, `<maxRetryBackoffMs>`, `<maxRetryJitterMs>`,
`<dropRateLimitedBatches>`, `<useProtobufApi>`, `<sender>`.

`<batch>` children include: `<maxItems>` (1000), `<maxBytes>` (4194304), `<timeoutMs>` (60000),
`<drainOnStop>`, `<sendQueueMaxBytes>`.

v1.x `<format>` / `<label><pattern>` elements were REMOVED in 2.0 - do not use.

**Decision: omit `<structuredMetadata>` entirely** (use the default). traceId travels in the log
line, so structured metadata is not needed and skipping it removes a Loki-side failure mode.

---

## 3. Spring Boot 3.4.1 OTLP tracing properties

| Property | Value |
|---|---|
| `management.otlp.tracing.endpoint` | full signal URL incl. `/v1/traces` |
| `management.otlp.tracing.transport` | `http` (default; `grpc` = 4317) |
| `management.tracing.enabled` | unchanged, still correct |
| `management.tracing.sampling.probability` | unchanged, still correct |

Namespace is `management.otlp.tracing.*`. There is **no** `management.opentelemetry.tracing.*`
in 3.4.x (`management.opentelemetry.*` only holds `resource-attributes`).

**Endpoint includes the signal path.** Corroborated from the 3.4 properties appendix: the sibling
metrics property `management.otlp.metrics.export.url` has documented default
`http://localhost:4318/v1/metrics` - i.e. Boot 3.4 expects the full signal path, not a base URL.
So: `http://tempo:4318/v1/traces`.

`io.opentelemetry:opentelemetry-exporter-otlp` needs **no explicit version** (Boot 3.4.1 manages
the `io.opentelemetry` group; OTel 1.41.x).

Env var forms (relaxed binding, dots -> `_`, uppercase, dashes dropped):
`MANAGEMENT_OTLP_TRACING_ENDPOINT`, `MANAGEMENT_OTLP_TRACING_TRANSPORT`,
`MANAGEMENT_TRACING_ENABLED`, `MANAGEMENT_TRACING_SAMPLING_PROBABILITY`.

**Not used:** `management.otlp.tracing.export.enabled` - listed by researcher-02 but not
independently confirmed for 3.4.1. `management.tracing.enabled` already covers the toggle, so
the plan avoids the unverified key.

---

## 4. Prometheus metrics registry (verified against Boot 3.4 docs)

Required dependency: `io.micrometer:micrometer-registry-prometheus` (NOT the deprecated
`-simpleclient` variant, which exists only for Pushgateway).

`/actuator/prometheus` is **not** exposed by default, but this repo already sets
`management.endpoints.web.exposure.include=*` (application.properties:45), so no property change
is needed - the dependency alone is sufficient.

---

## 5. Tempo config - verified from `example/docker-compose/single-binary/tempo.yaml` @ `v3.0.3`

Container must be started with `-target=all`:
`command: [ "-target=all", "-config.file=/etc/tempo.yaml" ]`

Upstream example binds receivers to `tempo:4317`/`tempo:4318` (its own service name). The plan
uses `0.0.0.0` instead so the config is independent of the compose service name and works for
host-published ports. `metrics_generator` + `overrides.defaults.metrics_generator` from the
upstream example are intentionally dropped (declared non-goal).

`stream_over_http_enabled: true` is present in every upstream example and is kept.

**No chown sidecar needed.** The `init`/busybox chown service in upstream examples exists only
because they bind-mount `./tempo-data`. This plan mounts no volume for Tempo, and the image
already ships `/var/tempo` pre-created `--chown=10001:10001 --chmod=0700` (verified in the
Tempo Dockerfile), so the container-local path is writable by the runtime user.

---

## 6. Loki - no config file required (verified from Dockerfile @ `v3.7.7`)

```
EXPOSE 3100
ENTRYPOINT [ "/usr/bin/loki" ]
CMD ["-config.file=/etc/loki/local-config.yaml"]
```

The image bakes `/etc/loki/local-config.yaml` (from `cmd/loki/loki-docker-config.yaml`) with
`auth_enabled: false`, `http_listen_port: 3100`, filesystem storage under `/loki`, and
`schema: v13` + `store: tsdb`. That accepts pushes as-is.

**Definitive: skip mounting a Loki config file.** Confirms researcher-03 on this point.

---

## 7. Healthchecks - NOT possible via exec for Tempo/Loki

Verified from upstream Dockerfiles:

- Loki `v3.7.7` final stage: `FROM gcr.io/distroless/static:nonroot`
- Tempo `v3.0.3` final stage: `FROM gcr.io/distroless/static-debian12`

Distroless static images contain **no shell, no `curl`, no `wget`**. A compose
`healthcheck` using `CMD-SHELL`/`curl`/`wget` will always fail for these two.

### CORRECTION

researcher-03 claimed "Loki & Prometheus: wget available (Alpine/busybox base)". False for Loki.

**Decision: define no `healthcheck:` blocks at all.** A mix of real and impossible healthchecks
is worse than none, and a failing healthcheck would make `docker compose up` look broken.
Readiness is asserted from the host in the smoke-test phase instead
(`/ready`, `/-/ready`, `/api/health`).

---

## 8. Grafana provisioning - the `$$` escaping trap

Grafana interpolates `${VAR}` and `$VAR` in provisioning files as environment variables. A
literal `$` must be escaped as `$$`.

Therefore in the provisioning YAML the tokens **must** be written:

- `$${__value.raw}` (Loki derivedFields)
- `$${__trace.traceId}` (Tempo tracesToLogsV2 query)

Writing `${__value.raw}` makes Grafana try to expand an env var named `__value.raw`, silently
producing an empty link. researcher-04 wrote the single-`$` form for `tracesToLogsV2.query`;
the official Grafana Loki provisioning example uses `$${__value.raw}`, confirming the double form.

`tracesToLogsV2` field set (confirmed from Grafana Tempo provisioning docs):
`datasourceUid`, `spanStartTimeShift`, `spanEndTimeShift`, `tags`, `filterByTraceID`,
`filterBySpanID`, `customQuery`, `query`. Time-shift format `'-1h'` / `'1h'`.

`derivedFields` entry keys: `name`, `matcherRegex`, `url`, `urlDisplayLabel`, `datasourceUid`.
`matcherType` is optional (default regex) - omitted here to match the official example.

Datasource UIDs are hardcoded (`prometheus`, `tempo`, `loki`) so cross-references resolve.

### Correlation query choice

traceId lives in the **log line**, not in a Loki label and not in structured metadata. So the
Tempo->Loki query must be a LogQL **line filter**, not a label filter. researcher-04 proposed
`'| trace_id = "..."'`, which is a structured-metadata label filter with no stream selector -
it would not match this setup and is not even valid LogQL on its own.

Used instead (stream selector that matches any `app` label, plus a line filter):

```
query: '{app=~".+"} |= "$${__trace.traceId}"'
```

with `customQuery: true`. This avoids hardcoding the application name while still supplying the
mandatory stream selector.

### Exemplars - SKIPPED

`exemplarTraceIdDestinations` is deliberately omitted. Exemplars are not in the acceptance
criteria, add a second correlation mechanism to debug, and researcher-04's "auto-emits" claim
was not independently verified. Recorded as a follow-up.

---

## 9. Derived-field regex for this app's log format

Loki message pattern written by the appender contains `[appName,traceId,spanId]`.
traceId = 32 lowercase hex, spanId = 16, both empty when no trace is active.

Regex (single capture group, rejects the empty-traceId case):

```
\[[^,\]]*,([0-9a-f]{32}),
```

Written **single-quoted** in YAML so backslashes stay literal. Contains no `$`, so no escaping
issue.

---

## 10. Codebase facts re-verified (not from sub-agents)

- All Spring context tests use `@ActiveProfiles("integration")` -
  `ApplicationTest.kt:17`, `FruitOrderingIntegrationTest.kt:37`, `TodoIntegrationTest.kt:40`,
  `WarehouseAllModuleTest.kt:18`, `WarehouseDirectDependenciesModuleTest.kt:31`,
  `WarehouseStandaloneModuleTest.kt:31`. Total: 6 files.
  => gating the Loki appender on profiles `local,docker` keeps `./gradlew check` untouched.
- `src/test/resources/application.properties` shadows the main one on the test classpath, so
  main `management.*` values do not apply during tests anyway.
- Logback only auto-loads `logback.xml`/`logback-test.xml`; `logback-spring.xml` is loaded by
  Spring Boot's `LogbackLoggingSystem`. Pure unit tests (e.g. `TodoDomainTest`) never
  initialize it.
- `/actuator/**` is `permitAll` in BOTH chains:
  `SpringModulithKotlinApplication.kt:89` (API chain) and `:123` (MVC chain).
- No `docker/` directory exists yet - it must be created.

---

## 11. Dockerfile runtime base image (scope added 2026-09-10)

### 11.1 The current base image NO LONGER EXISTS

`Dockerfile:20` is `FROM openjdk:21-jdk-slim`. Verified against Docker Hub **and** the registry:

```
hub.docker.com/v2/.../library/openjdk            -> 200   (repo still listed)
hub.docker.com/v2/.../library/openjdk/tags/21-jdk-slim -> 404
hub.docker.com/v2/.../library/openjdk/tags/21-slim     -> 404
docker manifest inspect openjdk:21-jdk-slim      -> NOT PULLABLE
```

Control check (same method, known-good tag):
`docker manifest inspect eclipse-temurin:21.0.12_8-jre-noble -> PULLABLE`.

**Consequence: `docker build` on this repo already fails on any machine without a warm local
cache.** That breaks `docker compose -f docker-compose-full.yml up` (which has `build:`) and the
`docker` job in `.github/workflows/ci.yml:66`. This is a pre-existing P1 breakage, not a
nice-to-have modernisation - it raises phase 03's priority.

The build stage is unaffected: `docker manifest inspect gradle:8.11-jdk21-alpine -> PULLABLE`
(Hub tag 200). Only the runtime stage needs changing.

### 11.2 Chosen tag

**`eclipse-temurin:21.0.12_8-jre-noble`**

- Verified pullable. Latest fully-versioned `jre-noble` tag; pushed 2026-09-09.
- `java -version` inside the image: `Temurin-21.0.12+8 (build 21.0.12+8-LTS)`.
- Fully-pinned (`21.0.12_8`) rather than the floating `21-jre-noble`, so Renovate tracks and
  proposes concrete patch bumps.

Tag naming was verified against the registry rather than assumed - the Temurin form is
`{version}_{build}-jre-{distro}` with **no** `v` prefix. Available 21 JRE variants:
`21-jre`, `21-jre-noble`, `21-jre-jammy`, `21-jre-alpine`, `21-jre-alpine-3.2x`,
`21-jre-ubi9-minimal`, `21-jre-ubi10-minimal`, plus Windows variants.

### 11.3 JRE is sufficient - JFR verified present (this was the real risk)

`SpringModulithKotlinApplication.kt:11-13` imports `jdk.jfr.Category`, `jdk.jfr.Event`,
`jdk.jfr.Label`, `jdk.jfr.Name`, and `TraceHandler.HTTPEvent` (`:221`) extends `jdk.jfr.Event`.
`TraceHandler` is registered as an interceptor at `:170`, so a missing `jdk.jfr` module would
throw `NoClassDefFoundError` **at startup**, not at build time. `spring-modulith-starter-insight`
also relies on JFR.

Verified empirically by running the image:

```
$ docker run --rm eclipse-temurin:21-jre-noble java --list-modules | grep -i jfr
jdk.jfr@21.0.12
jdk.management.jfr@21.0.12
```

Full module audit of the JRE image - every module this stack needs is present:

| Module | Needed for | Status |
|---|---|---|
| `jdk.jfr`, `jdk.management.jfr` | `HTTPEvent`, modulith-insight | PRESENT |
| `java.desktop` | `java.beans.PropertyDescriptor` - Spring bean introspection | PRESENT |
| `jdk.unsupported` | `sun.misc.Unsafe` - Jackson/ByteBuddy/Kotlin | PRESENT |
| `java.sql`, `java.sql.rowset`, `java.transaction.xa` | JPA/Hikari/MySQL/H2 | PRESENT |
| `java.instrument` | Spring agent/proxy support | PRESENT |
| `java.management`, `java.management.rmi` | Actuator/Micrometer JVM metrics | PRESENT |
| `java.naming` | JNDI/LDAP | PRESENT |
| `jdk.crypto.ec`, `java.security.jgss` | TLS to Auth0 JWKS | PRESENT |
| `java.net.http` | loki4j default JDK HttpClient sender | PRESENT |
| `java.xml`, `jdk.zipfs`, `jdk.localedata`, `jdk.charsets` | misc | PRESENT |

**Conclusion: no full JDK required.** `java.compiler` is also present in this JRE, so even
runtime compilation paths would work. No fallback to a `-jdk-` tag is needed.

### 11.4 Variant: glibc (noble), NOT alpine - and libc need not match the build stage

| Variant | amd64 compressed | libc | Arch coverage |
|---|---|---|---|
| `21.0.12_8-jre-noble` | 95 MB | glibc (Ubuntu 24.04 LTS) | amd64, arm64, ppc64le, s390x, riscv64 |
| `21-jre-jammy` | 94 MB | glibc (Ubuntu 22.04) | amd64, arm64, ppc64le, s390x |
| `21-jre-alpine` | 71 MB | musl | amd64, arm64 only |

**noble chosen.** The outgoing `openjdk:21-jdk-slim` was Debian/glibc, so noble is the
minimum-change swap: no libc change, no behavioural surprises in DNS resolution, locale/timezone
data, or default thread stack sizes. The 24 MB saving from alpine does not justify a libc switch
on a demo repo, and alpine drops three architectures.

**IMPORTANT - do not "fix" the libc mismatch.** The build stage is
`gradle:8.11-jdk21-alpine` (musl) while the runtime stage will be glibc. **This is correct and
intentional.** Only `build/libs/*.jar` crosses the stage boundary (`Dockerfile:24`), and a JAR is
architecture- and libc-neutral bytecode. There is no JNI, no native-image, and no compiled
artifact carried over. Aligning the two stages is unnecessary; a future reader should not
"harmonise" them.

---

## Open items carried into the plan

1. Tempo 3.x search indexing lag - trace-by-ID is immediate; free-text Search may need ~30-60s.
   Smoke test therefore looks up by traceId first.
2. Prometheus `v3.13.3` vs `v3.14.0` - both current; `v3.14.0` chosen as `latest`. Renovate will
   converge either way.
3. The `openjdk:21-jdk-slim` removal means the repo's Docker build is **currently broken** on a
   cold cache. Worth telling the user explicitly - it is independent of the LGTM migration.
