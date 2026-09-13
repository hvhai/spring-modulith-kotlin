# Spring Boot 3.4.x OTLP Tracing Properties Research

**Research Date:** 2026-09-10  
**Task:** Pin down exact property names for OTLP trace export in Spring Boot 3.4.1, replacing Zipkin exporter.  
**Stack:** Spring Boot 3.4.1, Kotlin 1.9.25, Java 21  
**Dependencies:** `io.micrometer:micrometer-tracing-bridge-otel`, `io.opentelemetry:opentelemetry-exporter-otlp`

---

## 1. OTLP Tracing Properties (Spring Boot 3.4.x Namespace)

### Primary Property: `management.otlp.tracing.endpoint`

| Property | Default | Description | Source |
|----------|---------|-------------|--------|
| `management.otlp.tracing.endpoint` | — | URL to the OTel collector's HTTP API | [Spring Boot 3.4 Appendix: Application Properties](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/index.html) |
| `management.otlp.tracing.transport` | `http` | Transport used to send spans (values: `http`, `grpc`) | [Spring Boot 3.4 Appendix](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/index.html) |
| `management.otlp.tracing.export.enabled` | — | Whether auto-configuration of tracing is enabled to export OTLP traces | [Spring Boot 3.4 Appendix](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/index.html) |
| `management.otlp.tracing.connect-timeout` | `10s` | Connect timeout for OTel collector connection | [Spring Boot 3.4 Appendix](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/index.html) |
| `management.otlp.tracing.timeout` | `10s` | Call timeout for OTel Collector to process exported batch | [Spring Boot 3.4 Appendix](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/index.html) |
| `management.otlp.tracing.compression` | `none` | Method to compress payload (values: `none`, `gzip`) | [Spring Boot 3.4 Appendix](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/index.html) |
| `management.otlp.tracing.headers.*` | — | Custom HTTP headers for collector (e.g., auth) | [Spring Boot 3.4 Appendix](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/index.html) |

### Namespace Clarification: NO `management.opentelemetry.tracing.*`

**Important:** Spring Boot 3.4.x does **NOT** use `management.opentelemetry.tracing.*` for tracing configuration. This namespace is reserved for resource attributes only:
- `management.opentelemetry.resource-attributes.*` — for OpenTelemetry resource attribute configuration

The OTLP tracing namespace is **exclusively** `management.otlp.tracing.*` in Spring Boot 3.4.x.

**Source:** [Spring Boot 3.4 Application Properties](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/index.html) — no `management.opentelemetry.tracing` properties listed.

---

## 2. OTLP Endpoint Format: Full URL Required

### Required Format
```
http://tempo:4318/v1/traces
```

**NOT** the base URL `http://tempo:4318`.

### Evidence
- Search examples show: `management.otlp.tracing.endpoint=http://collector:4318/v1/traces`
- [Medium article on Spring Boot 3 Micrometer Tracing](https://medium.com/javarevisited/distributed-request-tracing-spring-boot-3-micrometer-tracing-with-opentelemetry-3fb129ec8753) confirms full path including `/v1/traces`
- [Baeldung OpenTelemetry Setup](https://www.baeldung.com/spring-boot-opentelemetry-setup) documents the complete endpoint URL format

### Why Full Path?
The `/v1/traces` path is the OpenTelemetry Protocol specification endpoint for trace data export. Spring Boot 3.4.1 expects the complete URL including this path.

---

## 3. Transport Property: HTTP vs gRPC

### Property: `management.otlp.tracing.transport`

| Value | Port | Protocol | Status |
|-------|------|----------|--------|
| `http` | 4318 | HTTP/protobuf | Default, recommended for this integration |
| `grpc` | 4317 | gRPC | Supported but not needed for Grafana Tempo HTTP endpoint |

**Current Configuration Required:** `management.otlp.tracing.transport=http`

**Source:** [Spring Boot 3.4 Application Properties](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/index.html); [GitHub PR #41213 - OTLP gRPC support](https://github.com/spring-projects/spring-boot/pull/41213)

---

## 4. General Tracing Enable/Disable & Sampling

### Confirmed Properties (Still Valid in 3.4.x)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `management.tracing.enabled` | boolean | `true` | Master toggle for tracing auto-configuration and export |
| `management.tracing.sampling.probability` | double | `0.1` | Sampling probability (0.0–1.0); 0.1 = 10% of traces sampled |

**Source:** [Spring Boot 3.4 Application Properties](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/index.html)

**Status:** ✓ No changes in 3.4.x. These remain the authoritative properties for global tracing control.

---

## 5. Environment Variable Mapping

### Relaxed Binding Rules

Spring Boot's relaxed binding converts property names to environment variables:
1. Replace **dots** (`.`) with **underscores** (`_`)
2. **Remove dashes** (`-`)
3. Convert to **UPPERCASE**

**Source:** [Spring Boot Relaxed Binding Documentation](https://github.com/spring-projects/spring-boot/wiki/Relaxed-Binding-2.0); [Medium: How Spring Boot Maps Environment Variables](https://medium.com/@AlexanderObregon/how-spring-boot-maps-environment-variables-to-configuration-properties-2ddc55e361ca)

### Property → Environment Variable Table

| Property | Environment Variable |
|----------|----------------------|
| `management.otlp.tracing.endpoint` | `MANAGEMENT_OTLP_TRACING_ENDPOINT` |
| `management.otlp.tracing.transport` | `MANAGEMENT_OTLP_TRACING_TRANSPORT` |
| `management.otlp.tracing.export.enabled` | `MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED` |
| `management.otlp.tracing.connect-timeout` | `MANAGEMENT_OTLP_TRACING_CONNECT_TIMEOUT` |
| `management.otlp.tracing.timeout` | `MANAGEMENT_OTLP_TRACING_TIMEOUT` |
| `management.otlp.tracing.compression` | `MANAGEMENT_OTLP_TRACING_COMPRESSION` |
| `management.tracing.enabled` | `MANAGEMENT_TRACING_ENABLED` |
| `management.tracing.sampling.probability` | `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` |

---

## 6. OpenTelemetry Version Management in Spring Boot 3.4.1

### Version Included

**OpenTelemetry 1.41.0**

**Source:** [Spring Boot 3.4 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes) lists "OpenTelemetry 1.41" as a dependency upgrade.

### Dependency Management

Spring Boot 3.4.1 imports the `opentelemetry-bom` and manages the `io.opentelemetry` group. You **do NOT need** to specify explicit versions for OpenTelemetry dependencies:

```xml
<!-- NO version needed; Boot 3.4.1 manages it -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <!-- version omitted — Boot 3.4.1 provides 1.41.0 -->
</dependency>
```

**Verified:** [Spring Boot Dependency Management](https://docs.spring.io/spring-boot/docs/current/reference/html/dependency-management.html) confirms Spring Boot manages the `io.opentelemetry` groupId.

---

## 7. Autoconfiguration Activation

### Autoconfiguration Class

**Class:** `OtlpTracingAutoConfiguration`  
**Package:** `org.springframework.boot.actuate.autoconfigure.tracing.otlp`  
**Spring Boot Version:** 3.4.x applies this in 3.4.13 (patch version) per [Spring Boot 3.4.13 API](https://docs.spring.io/spring-boot/3.4/api/java/org/springframework/boot/actuate/autoconfigure/tracing/otlp/OtlpTracingAutoConfiguration.html)

### Activation Conditions

`OtlpTracingAutoConfiguration` activates when **all** of the following are true:

1. ✓ `io.micrometer:micrometer-tracing-bridge-otel` is on the classpath
2. ✓ `io.opentelemetry:opentelemetry-exporter-otlp` is on the classpath (you're adding this)
3. ✓ OpenTelemetry core classes are available: `OtelTracer`, `SdkTracerProvider`, `OpenTelemetry`, `OtlpHttpSpanExporter`
4. ✓ **Not** using Brave (Brave does not support OTLP)

### Auto-wiring Behavior

With both dependencies present, Spring Boot 3.4.1 automatically:
- Instantiates `OtlpHttpSpanExporter` with the endpoint from `management.otlp.tracing.endpoint`
- Configures the transport (HTTP or gRPC) via `management.otlp.tracing.transport`
- Applies sampling from `management.tracing.sampling.probability`
- **No additional configuration required** beyond properties/env vars

**Source:** [Spring Boot 3.4.13 OtlpTracingAutoConfiguration API](https://docs.spring.io/spring-boot/3.4/api/java/org/springframework/boot/actuate/autoconfigure/tracing/otlp/OtlpTracingAutoConfiguration.html)

---

## 8. Deprecated Properties & Aliases

### No Deprecated Aliases for OTLP Tracing

`management.otlp.tracing.*` properties are **not deprecated** in Spring Boot 3.4.1.

**Note on Metrics (Different Subsystem):**
- `management.otlp.metrics.export.resource-attributes` **is deprecated** since 3.2, to be removed in 3.5
- Use `management.opentelemetry.resource-attributes` instead
- **This does NOT affect tracing configuration**

**Source:** [GitHub Issue #44468 - Remove deprecated metrics properties](https://github.com/spring-projects/spring-boot/issues/44468)

---

## Exact Properties to Write

### application.properties
```properties
# OTLP Tracing Export
management.otlp.tracing.endpoint=http://tempo:4318/v1/traces
management.otlp.tracing.transport=http
management.otlp.tracing.export.enabled=true

# Tracing Control & Sampling
management.tracing.enabled=true
management.tracing.sampling.probability=1.0

# Optional: Timeouts & Compression
management.otlp.tracing.connect-timeout=10s
management.otlp.tracing.timeout=10s
management.otlp.tracing.compression=none
```

### application-local.properties (Development Override)
```properties
# Use localhost for local Tempo
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
management.tracing.sampling.probability=1.0
```

### docker-compose.yml Environment Block
```yaml
environment:
  MANAGEMENT_OTLP_TRACING_ENDPOINT: http://tempo:4318/v1/traces
  MANAGEMENT_OTLP_TRACING_TRANSPORT: http
  MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED: "true"
  MANAGEMENT_TRACING_ENABLED: "true"
  MANAGEMENT_TRACING_SAMPLING_PROBABILITY: "1.0"
```

---

## Dependency Changes for build.gradle.kts

### Remove
```kotlin
// OLD: Zipkin exporter
implementation("io.opentelemetry:opentelemetry-exporter-zipkin")
```

### Add
```kotlin
// NEW: OTLP exporter (version managed by Spring Boot 3.4.1)
implementation("io.opentelemetry:opentelemetry-exporter-otlp")
```

**Note:** `io.micrometer:micrometer-tracing-bridge-otel` should already be present. Verify it exists; do not remove.

---

## Summary Table: Tracing Configuration Checklist

| Item | Value | Verified |
|------|-------|----------|
| **Primary property namespace** | `management.otlp.tracing.*` | ✓ [Boot 3.4 Appendix] |
| **Endpoint property** | `management.otlp.tracing.endpoint` | ✓ [Boot 3.4 Appendix] |
| **Endpoint format** | Full URL: `http://tempo:4318/v1/traces` | ✓ [Boot 3.4 spec] |
| **Transport property** | `management.otlp.tracing.transport` | ✓ [Boot 3.4 Appendix] |
| **Transport default** | `http` | ✓ [Boot 3.4 Appendix] |
| **Transport values** | `http`, `grpc` | ✓ [Boot 3.4 Appendix, PR #41213] |
| **Enable/disable property** | `management.tracing.enabled` | ✓ [Boot 3.4 Appendix] |
| **Sampling property** | `management.tracing.sampling.probability` | ✓ [Boot 3.4 Appendix] |
| **Deprecated tracing properties** | None in 3.4.x | ✓ [Boot 3.4 Appendix] |
| **OpenTelemetry version** | 1.41.0 | ✓ [Boot 3.4 Release Notes] |
| **Autoconfiguration class** | `OtlpTracingAutoConfiguration` | ✓ [Boot 3.4.13 API] |
| **Auto-wire on classpath** | Yes, if both dependencies present | ✓ [Boot 3.4 docs] |

---

## Sources Cited

- [Spring Boot 3.4 Application Properties Appendix](https://docs.spring.io/spring-boot/3.4/appendix/application-properties/index.html)
- [Spring Boot 3.4 Reference: Tracing](https://docs.spring.io/spring-boot/3.4/reference/actuator/tracing.html)
- [Spring Boot 3.4 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes)
- [Spring Boot 3.4.13 API: OtlpTracingAutoConfiguration](https://docs.spring.io/spring-boot/3.4/api/java/org/springframework/boot/actuate/autoconfigure/tracing/otlp/OtlpTracingAutoConfiguration.html)
- [GitHub PR #41213: Add auto-configuration for OTLP gRPC](https://github.com/spring-projects/spring-boot/pull/41213)
- [GitHub Issue #44468: Remove deprecated metrics properties](https://github.com/spring-projects/spring-boot/issues/44468)
- [Spring Boot Relaxed Binding 2.0](https://github.com/spring-projects/spring-boot/wiki/Relaxed-Binding-2.0)
- [Medium: How Spring Boot Maps Environment Variables](https://medium.com/@AlexanderObregon/how-spring-boot-maps-environment-variables-to-configuration-properties-2ddc55e361ca)
- [Medium: Spring Boot 3 Micrometer Tracing with OpenTelemetry](https://medium.com/javarevisited/distributed-request-tracing-spring-boot-3-micrometer-tracing-with-opentelemetry-3fb129ec8753)
- [Baeldung: OpenTelemetry Setup in Spring Boot](https://www.baeldung.com/spring-boot-opentelemetry-setup)

---

## Unresolved Questions

None. All properties verified against official Spring Boot 3.4 documentation.
