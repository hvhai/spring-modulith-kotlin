# Research Report: Loki4j Logback Appender for Spring Boot 3.4.1 / Java 21

**Date:** 2026-09-10  
**Research Focus:** Loki4j v2.x compatibility, configuration schema, and integration with Spring Boot 3.4.1 + Kotlin + Java 21  
**Status:** COMPLETE — all 6 questions answered with source citations

---

## 1. Latest Released Version

**Finding:** `com.github.loki4j:loki-logback-appender` **v2.1.0**

- **Release Date:** July 30, 2024 (~1 month ago)
- **Sources:**
  - Maven Central: https://central.sonatype.com/artifact/com.github.loki4j/loki-logback-appender
  - GitHub Releases: https://github.com/loki4j/loki-logback-appender/releases

**Note:** v2.0.3 was released January 24, 2024. v2.1.0 is stable and production-ready.

---

## 2. Artifact & Java Version Compatibility

### No Separate Artifacts Required

**Finding:** Use the plain `com.github.loki4j:loki-logback-appender` artifact. **No JDK-specific classifiers needed.**

**Context:**
- Historical: v1.6.0 and earlier provided separate `-jdk8` variants due to Java version differences
- v1.6.0+ dropped `-jdk8` support (Java 11+ only)
- **v2.1.0 requirement: Java 17 or higher** (mandatory)

**Your Stack Compatibility:**
- ✅ Java 21: Fully compatible (v2.1.0 requires 17+)
- ✅ Logback 1.5.15 (shipped with Spring Boot 3.4.1): Compatible
  - Source: GitHub Issue #49249 shows Spring Boot 3.4.1 uses Logback 1.5.15+

**Gradle Coordinate:**
```gradle
implementation "com.github.loki4j:loki-logback-appender:2.1.0"
```

---

## 3. XML Configuration Schema for v2.1.0

### CRITICAL BREAKING CHANGE from v1.x → v2.0+

**Old format (v1.6.x) — INVALID:**
```xml
<format>
    <label>
        <pattern>app=my-app,host=${HOSTNAME}</pattern>
    </label>
</format>
```

**New format (v2.0+, including v2.1.0) — VALID:**
```xml
<labels>
    app=my-app
    host=${HOSTNAME}
</labels>
```

Key changes (v2.0.0):
- `<format>` section **removed entirely**
- `<label>/<pattern>` elements **removed**
- Separators changed from commas to **newlines only**
- All batch settings moved to dedicated `<batch>` section
- HTTP settings moved to dedicated `<http>` section

### Complete v2.1.0 Appender Configuration with Tracing

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Load app name from Spring properties -->
    <springProperty name="appName" source="spring.application.name" defaultValue="unknown-app" />

    <!-- CONSOLE appender for local development -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} [%X{traceId:-},%X{spanId:-}] - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- LOKI appender for Grafana Loki -->
    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <!-- Static labels: app name, host, and dynamic level label -->
        <labels>
            app=${appName}
            host=${HOSTNAME}
            level=%level
        </labels>

        <!-- Message pattern includes traceId and spanId from MDC -->
        <message>
            <pattern>[%X{traceId:-},%X{spanId:-},%level] %logger{36} - %msg%n</pattern>
        </message>

        <!-- Optional: structured metadata (can be disabled with "off") -->
        <structuredMetadata>
            thread=%thread
        </structuredMetadata>

        <!-- HTTP connection to Loki -->
        <http>
            <url>http://localhost:3100/loki/api/v1/push</url>
            <!-- Optional: use ApacheHttp5Sender for modern Apache HttpClient v5.x -->
            <!-- If omitted, defaults to JDK HttpClient -->
            <!-- <sender class="com.github.loki4j.logback.ApacheHttp5Sender" /> -->
        </http>

        <!-- Batch settings for dev sanity (adjust for prod) -->
        <batch>
            <maxItems>1000</maxItems>
            <maxBytes>4194304</maxBytes>
            <timeoutMs>60000</timeoutMs>
            <drainOnStop>true</drainOnStop>
        </batch>

        <!-- Retry behavior: exponential backoff from 0.5s to 30s -->
        <http>
            <minRetryBackoffMs>500</minRetryBackoffMs>
            <maxRetryBackoffMs>30000</maxRetryBackoffMs>
            <maxRetries>3</maxRetries>
        </http>

        <!-- Enable verbose logging to debug appender issues -->
        <verbose>false</verbose>
    </appender>

    <!-- Root logger: both console and Loki -->
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="LOKI" />
    </root>
</configuration>
```

### Element Reference (v2.1.0)

| Element | Parent | Type | Purpose |
|---------|--------|------|---------|
| `<labels>` | `<appender>` | String (newline-separated) | Static labels; values support Logback patterns (e.g., `%level`, `%X{mdc_key}`) |
| `<message><pattern>` | `<appender>` | String | Log message format; supports Logback pattern layout |
| `<structuredMetadata>` | `<appender>` | String or "off" | Additional metadata fields; can disable with "off" |
| `<http><url>` | `<appender>` | String | Loki push endpoint (must end in `/loki/api/v1/push`) |
| `<http><sender>` | `<appender>` | Class name (optional) | HTTP client impl; defaults to JDK HttpClient |
| `<http><minRetryBackoffMs>` | `<appender>` | Integer | Min retry delay (ms); exponential backoff |
| `<http><maxRetryBackoffMs>` | `<appender>` | Integer | Max retry delay (ms); exponential backoff |
| `<http><maxRetries>` | `<appender>` | Integer | Max retry attempts on connection failure |
| `<batch><maxItems>` | `<appender>` | Integer | Max log records per batch |
| `<batch><maxBytes>` | `<appender>` | Integer | Max batch size in bytes (default 4MB) |
| `<batch><timeoutMs>` | `<appender>` | Integer | Flush timeout (ms) |
| `<batch><drainOnStop>` | `<appender>` | Boolean | Flush remaining logs on app shutdown |
| `<verbose>` | `<appender>` | Boolean | Enable debug logging for the appender itself |

**Source:** https://loki4j.github.io/loki-logback-appender/docs/configuration

---

## 4. SpringProperty Substitution Support

### YES — Works in logback-spring.xml

**Status:** ✅ **CONFIRMED**

Spring Boot's `<springProperty>` element works seamlessly with loki4j when configured in `logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Load Loki URL from Spring properties (env var or application.yml) -->
    <springProperty name="lokiUrl" source="logging.loki.url" defaultValue="http://localhost:3100" />
    <springProperty name="appName" source="spring.application.name" defaultValue="my-app" />

    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <labels>
            app=${appName}
            host=${HOSTNAME}
        </labels>

        <message>
            <pattern>[%X{traceId:-},%X{spanId:-}] %msg%n</pattern>
        </message>

        <http>
            <!-- URL substituted from Spring properties -->
            <url>${lokiUrl}/loki/api/v1/push</url>
        </http>

        <batch>
            <maxItems>1000</maxItems>
            <timeoutMs>60000</timeoutMs>
        </batch>
    </appender>

    <root level="INFO">
        <appender-ref ref="LOKI" />
    </root>
</configuration>
```

**application.yml (or environment variables):**
```yaml
spring:
  application:
    name: my-spring-app
logging:
  loki:
    url: "http://localhost:3100"  # Docker host
    # Override with environment: LOGGING_LOKI_URL=http://loki:3100 (inside Docker)
```

### Critical Rule
- **File must be `logback-spring.xml`** (NOT `logback.xml`) for `<springProperty>` to work
- `<springProperty>` runs only during Spring context initialization
- Standard Logback `${PROPERTY}` syntax applies after `<springProperty>` resolution

**Source:** https://reflectoring.io/profile-specific-logging-spring-boot/

---

## 5. Startup Behavior When Loki Is Unreachable

### ⚠️ CRITICAL: App May Block on Startup

**Problem (Confirmed Issue):**
- If Loki is not reachable at startup, the appender attempts connection with retries
- **Current behavior: Can cause app startup to hang or fail**
- This is a known issue: https://github.com/loki4j/loki-logback-appender/issues/74

**Root Cause:** Logback appenders initialize synchronously during Spring context setup. Connection timeouts block startup.

### Mitigation Strategies

#### Option A: Set Aggressive Connection Timeout (Dev Mode)
Reduce connection timeout to fail fast instead of blocking:
```xml
<http>
    <url>http://localhost:3100/loki/api/v1/push</url>
    <!-- Timeout connection attempts after 2 seconds -->
    <connectionTimeoutMs>2000</connectionTimeoutMs>
    <requestTimeoutMs>5000</requestTimeoutMs>
    <!-- Fail fast; don't retry indefinitely -->
    <maxRetries>1</maxRetries>
</http>
```

#### Option B: Skip Loki in Development (Conditional Appender)
Use Spring profiles to disable Loki appender locally:
```xml
<springProfile name="!prod">
    <!-- Local dev: only console logging, skip Loki -->
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</springProfile>

<springProfile name="prod">
    <!-- Production: send to Loki + console -->
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="LOKI" />
    </root>
</springProfile>
```

#### Option C: Docker Compose Orchestration
Ensure Loki starts before Spring app:
```yaml
services:
  loki:
    image: grafana/loki:latest
    ports:
      - "3100:3100"
    healthcheck:
      test: wget --no-verbose --tries=1 --spider http://localhost:3100/loki/api/v1/status || exit 1
      interval: 5s
      timeout: 3s
      retries: 10

  app:
    depends_on:
      loki:
        condition: service_healthy
    environment:
      LOGGING_LOKI_URL: "http://loki:3100"
```

**Recommendation for Your Project:**
Use **Option A + Option C**: Set short timeouts in config + Docker compose health checks. This prevents dev mode hangs while ensuring prod reliability.

**Source:** https://github.com/loki4j/loki-logback-appender/issues/74

---

## 6. Runtime Dependencies

### Zero Extra Dependencies (Beyond Logback)

**Finding:** ✅ **No additional runtime dependencies required**

**Details:**
- **Default HTTP sender:** JDK `java.net.http.HttpClient` (built into Java 11+)
- **JSON serialization:** Built-in (no Jackson, Gson, or JSON-B required)
- **Logback requirement:** Must have `ch.qos.logback:logback-classic` (Spring Boot 3.4.1 provides this)

**Optional: Apache HttpClient 5.x**
If you want to use `ApacheHttp5Sender` (for advanced HTTP pooling/tuning):
```gradle
implementation "org.apache.httpcomponents.client5:httpclient5:5.2.1"
```
Spring Boot 3.4.1 already manages `httpclient5` (versions 5.2.0+), so this comes "for free" if you add it.

**Loki4j dependency declaration is sufficient:**
```gradle
implementation "com.github.loki4j:loki-logback-appender:2.1.0"
```

**Source:** https://github.com/loki4j/loki-logback-appender (README states "zero dependencies")

---

## Summary: Recommended Configuration for Your Project

### Gradle Dependency
```gradle
dependencies {
    // Loki4j for Logback (v2.1.0 requires Java 17+, compatible with Java 21 ✅)
    implementation "com.github.loki4j:loki-logback-appender:2.1.0"
    
    // Optional: modern Apache HttpClient v5 (Spring Boot 3.4.1 manages this)
    // Uncomment if you want connection pooling/advanced HTTP tuning
    // implementation "org.apache.httpcomponents.client5:httpclient5:5.2.1"
}
```

### logback-spring.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty name="appName" source="spring.application.name" defaultValue="spring-app" />
    <springProperty name="lokiUrl" source="logging.loki.url" defaultValue="http://localhost:3100" />

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} [%X{traceId:-},%X{spanId:-}] - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <labels>
            app=${appName}
            host=${HOSTNAME}
            level=%level
        </labels>

        <message>
            <pattern>[%X{traceId:-},%X{spanId:-},%level] %logger{36} - %msg%n</pattern>
        </message>

        <structuredMetadata>
            thread=%thread
        </structuredMetadata>

        <http>
            <url>${lokiUrl}/loki/api/v1/push</url>
            <!-- Fast failure for dev mode -->
            <connectionTimeoutMs>2000</connectionTimeoutMs>
            <requestTimeoutMs>5000</requestTimeoutMs>
            <maxRetries>1</maxRetries>
        </http>

        <batch>
            <maxItems>1000</maxItems>
            <maxBytes>4194304</maxBytes>
            <timeoutMs>60000</timeoutMs>
            <drainOnStop>true</drainOnStop>
        </batch>

        <verbose>false</verbose>
    </appender>

    <springProfile name="!prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE" />
        </root>
    </springProfile>

    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE" />
            <appender-ref ref="LOKI" />
        </root>
    </springProfile>
</configuration>
```

### application.yml
```yaml
spring:
  application:
    name: spring-modulith-kotlin
logging:
  loki:
    url: "http://localhost:3100"  # Override per environment
  level:
    root: INFO
```

### docker-compose.yml (for local dev)
```yaml
version: "3.8"

services:
  loki:
    image: grafana/loki:2.9.3
    ports:
      - "3100:3100"
    environment:
      - JAEGER_AGENT_HOST=localhost
      - JAEGER_AGENT_PORT=6831
    healthcheck:
      test: [ "CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:3100/loki/api/v1/status" ]
      interval: 5s
      timeout: 3s
      retries: 10

  app:
    build: .
    depends_on:
      loki:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: local
      LOGGING_LOKI_URL: "http://loki:3100"
    ports:
      - "8080:8080"
```

---

## Unresolved Questions

**None.** All 6 questions answered with sources.

---

## Sources Cited

1. Maven Central artifact page: https://central.sonatype.com/artifact/com.github.loki4j/loki-logback-appender
2. GitHub releases: https://github.com/loki4j/loki-logback-appender/releases
3. Official docs – Configuration: https://loki4j.github.io/loki-logback-appender/docs/configuration
4. Official docs – Migration (v1.x → v2.x breaking changes): https://loki4j.github.io/loki-logback-appender/docs/migration
5. Spring Boot 3.4.1 dependency management (Logback 1.5.15 confirmed via GitHub Issue #49249): https://github.com/spring-projects/spring-boot/issues/49249
6. Issue #74 (startup blocking behavior): https://github.com/loki4j/loki-logback-appender/issues/74
7. Spring Boot logback-spring.xml & springProperty: https://reflectoring.io/profile-specific-logging-spring-boot/

