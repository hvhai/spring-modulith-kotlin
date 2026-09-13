# Phase 01 — App Observability Dependencies & Config

## Context Links

- [plan.md](plan.md)
- [research/researcher-05-verified-findings-synthesis.md](research/researcher-05-verified-findings-synthesis.md) — authoritative values
- [research/researcher-01-loki4j-appender.md](research/researcher-01-loki4j-appender.md) (see corrections in -05)
- [research/researcher-02-boot34-otlp-properties.md](research/researcher-02-boot34-otlp-properties.md)

## Overview

- **Priority:** P1 (blocks 03, 04, 05)
- **Status:** completed
- **Effort:** 1h
- Swap the Zipkin span exporter for OTLP, add the Prometheus meter registry, add the loki4j
  logback appender behind a new `logback-spring.xml`, and repoint the tracing properties.

## Key Insights

1. **`/actuator/prometheus` does not exist today.** No `micrometer-registry-prometheus` on the
   classpath (`build.gradle.kts:52-56` has actuator, modulith-insight, tracing-bridge-otel,
   exporter-zipkin only). Adding the registry is sufficient — the repo already sets
   `management.endpoints.web.exposure.include=*` (`application.properties:45`), so no
   exposure property change is needed.
2. **A hand-written `logback-spring.xml` silently breaks the existing correlation pattern.**
   `application.properties:43` sets `logging.pattern.correlation`. That property is only honored
   through Spring Boot's `defaults.xml`. The new file MUST `<include>` Boot's `defaults.xml` and
   `console-appender.xml`, otherwise console logs lose `[app,traceId,spanId]` and AC #4 fails.
3. **The Loki appender must not run during tests.** `src/main/resources` is on the test runtime
   classpath, so Spring Boot would load `logback-spring.xml` in every `@SpringBootTest`. All six
   Spring-context test classes use `@ActiveProfiles("integration")` (`ApplicationTest.kt:17`,
   `FruitOrderingIntegrationTest.kt:37`, `TodoIntegrationTest.kt:40`,
   `WarehouseAllModuleTest.kt:18`, `WarehouseDirectDependenciesModuleTest.kt:31`,
   `WarehouseStandaloneModuleTest.kt:31`). Gating the appender on profiles `local,docker`
   therefore keeps `./gradlew check` (AC #5) green with zero test edits.
   Pure unit tests never load the file at all — Logback only auto-loads `logback.xml`, while
   `logback-spring.xml` is loaded by Spring's `LogbackLoggingSystem`.
4. **The OTLP endpoint takes the full signal path**, `.../v1/traces`, not a base URL.
5. Existing tracing on/off split is preserved: OFF in the default profile, ON in `local`.
   Mode A therefore requires `--spring.profiles.active=local` (see Risk R4).

## Requirements

### Functional

- Remove `opentelemetry-exporter-zipkin`; add `opentelemetry-exporter-otlp` (no explicit
  version — managed by Boot 3.4.1).
- Add `io.micrometer:micrometer-registry-prometheus` (no version — managed).
- Add `com.github.loki4j:loki-logback-appender:2.1.0` (explicit version — not managed).
- Replace `management.zipkin.tracing.endpoint` with `management.otlp.tracing.endpoint` in both
  properties files and in the startup property dump.
- Loki URL overridable per run mode via a Spring property with a working default.

### Non-functional

- No change to security chains, exposure settings, or sampling values.
- `./gradlew check` must behave exactly as before.
- No credentials introduced.

## Architecture

```
                    ┌─────────────────────────────┐
   scrape (pull)    │   Spring Boot app           │
Prometheus ────────>│  /actuator/prometheus       │  micrometer-registry-prometheus
                    │                             │
                    │  Micrometer Tracing ──OTLP─>│──> Tempo :4318 /v1/traces
                    │  (bridge-otel + exporter-otlp)
                    │                             │
                    │  Logback ──loki4j push─────>│──> Loki :3100 /loki/api/v1/push
                    └─────────────────────────────┘
                       traceId in MDC feeds BOTH the OTLP span and the log line
```

Loki URL indirection: `logback-spring.xml` reads Spring property `loki.url`
(default `http://localhost:3100`). Mode B overrides it with env `LOKI_URL=http://loki:3100`
(relaxed binding resolves `LOKI_URL` -> `loki.url`).

## Related Code Files

**Modify**

- `build.gradle.kts` — lines 52–56 (`// monitoring` block)
- `src/main/resources/application.properties` — line 50
- `src/main/resources/application-local.properties` — line 14
- `src/main/kotlin/com/codehunter/spring_modulith_kotlin/SpringModulithKotlinApplication.kt` — line 53

**Create**

- `src/main/resources/logback-spring.xml`

**Delete** — none.

## Implementation Steps

### 1. `build.gradle.kts` — monitoring block

Replace line 56 and extend the block so it reads:

```kotlin
	// monitoring
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.modulith:spring-modulith-starter-insight")
	implementation("io.micrometer:micrometer-tracing-bridge-otel")
	implementation("io.opentelemetry:opentelemetry-exporter-otlp")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("com.github.loki4j:loki-logback-appender:2.1.0")
```

`io.opentelemetry:opentelemetry-exporter-zipkin` is deleted. Keep the file's existing tab
indentation.

### 2. `src/main/resources/application.properties`

Replace line 50 with:

```properties
management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces
management.otlp.tracing.transport=http
loki.url=http://localhost:3100
```

Leave lines 45–49 untouched (`exposure.include=*`, `show-values`, `sampling.probability=1.0`,
`tracing.enabled=false`).

### 3. `src/main/resources/application-local.properties`

Replace line 14 with:

```properties
management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces
management.otlp.tracing.transport=http
```

`management.tracing.enabled=true` (line 13) stays. `loki.url` inherits the default.

### 4. `SpringModulithKotlinApplication.kt:53`

Replace the literal `"management.zipkin.tracing.endpoint"` with:

```kotlin
            "management.otlp.tracing.endpoint",
```

Nothing else in the file changes.

### 5. Create `src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Boot defaults keep logging.pattern.correlation working on the console. -->
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <springProperty scope="context" name="appName"
                    source="spring.application.name" defaultValue="spring-modulith-kotlin"/>
    <springProperty scope="context" name="lokiUrl"
                    source="loki.url" defaultValue="http://localhost:3100"/>

    <!-- Log shipping is only wired up where a Loki instance is expected to exist. -->
    <springProfile name="local,docker">
        <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
            <labels>
                app=${appName}
                host=${HOSTNAME}
                level=%level
            </labels>
            <message>
                <pattern>%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [${appName},%X{traceId:-},%X{spanId:-}] [%thread] %logger{40} - %msg%n%ex</pattern>
            </message>
            <http>
                <url>${lokiUrl}/loki/api/v1/push</url>
                <connectionTimeoutMs>5000</connectionTimeoutMs>
                <requestTimeoutMs>10000</requestTimeoutMs>
                <maxRetries>2</maxRetries>
                <minRetryBackoffMs>500</minRetryBackoffMs>
                <maxRetryBackoffMs>5000</maxRetryBackoffMs>
            </http>
            <batch>
                <maxItems>1000</maxItems>
                <timeoutMs>5000</timeoutMs>
                <drainOnStop>true</drainOnStop>
            </batch>
            <verbose>false</verbose>
        </appender>
    </springProfile>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <springProfile name="local,docker">
            <appender-ref ref="LOKI"/>
        </springProfile>
    </root>
</configuration>
```

Notes for the implementer:

- `<labels>` entries are **newline**-separated (v2 schema). Commas are the removed v1 syntax.
- Exactly **one** `<http>` block; retry knobs live inside it.
- `<message><pattern>` deliberately embeds `[${appName},%X{traceId:-},%X{spanId:-}]` — phase 02's
  Loki derived-field regex matches exactly this shape. Changing one requires changing the other.
- `batch/timeoutMs` is lowered from the 60000 default to 5000 so a smoke test sees logs quickly.
- Do **not** add `<structuredMetadata>`; the default is intentional.

### 6. Compile

```powershell
.\gradlew.bat compileKotlin
```

## Todo List

- [x] `build.gradle.kts`: drop `opentelemetry-exporter-zipkin`
- [x] `build.gradle.kts`: add `opentelemetry-exporter-otlp`
- [x] `build.gradle.kts`: add `micrometer-registry-prometheus`
- [x] `build.gradle.kts`: add `loki-logback-appender:2.1.0`
- [x] `application.properties`: zipkin endpoint -> otlp endpoint + transport + `loki.url`
- [x] `application-local.properties`: zipkin endpoint -> otlp endpoint + transport
- [x] `SpringModulithKotlinApplication.kt:53`: property key renamed
- [x] `logback-spring.xml` created with Boot includes + profile-gated LOKI appender
- [x] `.\gradlew.bat compileKotlin` clean
- [x] `.\gradlew.bat check` green (no test edits)

## Success Criteria

- `grep -ri zipkin build.gradle.kts src/` returns nothing.
- `./gradlew check` passes with the same test count as before the change.
- Booting with `--spring.profiles.active=local` logs `The following 1 profile is active: "local"`
  and the startup dump prints `management.otlp.tracing.endpoint = http://127.0.0.1:4318/v1/traces`.
- `curl localhost:8080/actuator/prometheus` returns a non-empty text exposition payload.
- Console log lines still contain `[spring-modulith-kotlin,<traceId>,<spanId>]`.

## Risk Assessment

| # | Risk | L×I | Mitigation |
|---|---|---|---|
| R1 | Custom `logback-spring.xml` drops Boot's default console pattern, killing correlation in console logs and breaking AC #4 | Med×High | Mandatory `<include>` of `defaults.xml` + `console-appender.xml`; success criterion explicitly checks the console line |
| R2 | Loki appender activates during `./gradlew check`, spamming connection errors / slowing the build | Med×Med | `<springProfile name="local,docker">` gate; all 6 Spring test classes use profile `integration` (verified) |
| R3 | Wrong loki4j XML schema (v1 syntax) => Logback config error at boot | Med×High | v2 schema pinned in step 5; single `<http>`, newline-separated `<labels>` |
| R4 | Plain `./gradlew bootRun` (no profile) produces **no traces** because `management.tracing.enabled=false` in the default profile — AC #3 appears to fail | High×Med | Mode A is documented as `--spring.profiles.active=local`; see Unresolved Question 1 |
| R5 | Loki unreachable on host boot => appender retries and logs noise | Med×Low | Short timeouts + `maxRetries=2`; loki4j sends asynchronously and never blocks app startup |
| R6 | OTLP endpoint given as base URL without `/v1/traces` => spans silently dropped | Med×High | Full signal path pinned in steps 2/3; smoke test asserts a trace lands |

## Security Considerations

- **Known, accepted:** adding `micrometer-registry-prometheus` makes `/actuator/prometheus`
  publicly reachable, because `management.endpoints.web.exposure.include=*`
  (`application.properties:45`) combines with `/actuator/**` being `permitAll` in **both**
  filter chains (`SpringModulithKotlinApplication.kt:89` API chain, `:123` MVC chain).
  This exposes JVM/HTTP/datasource metrics anonymously. **Do not change the security config in
  this phase** — out of scope. Phase 04 documents it; hardening is a follow-up.
- No credentials added. `loki.url` and the OTLP endpoint are non-secret hostnames.
- Loki is configured with `auth_enabled: false` (dev only) — never expose it beyond localhost.

## Next Steps

- Blocks phase 03 (needs the final env-var names `MANAGEMENT_OTLP_TRACING_ENDPOINT`, `LOKI_URL`)
  and phase 04.
- Runs in parallel with phase 02 (disjoint files).
- Follow-up (not this plan): restrict actuator exposure and add an auth'd actuator chain.
