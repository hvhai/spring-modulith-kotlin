# System Architecture

## Architectural style

The system is a **modular monolith**: one deployable Spring Boot process containing independently organized business modules. Spring Modulith provides module discovery, verification, application-event support, persistence integration, and insight/observability support.

## Runtime layers

1. **HTTP/UI layer** — Spring MVC controllers, Thymeleaf templates, static resources, Swagger UI.
2. **Security layer** — API JWT resource-server chain and MVC OAuth2-login chain.
3. **Application modules** — Todo and fruit-ordering business capabilities.
4. **Persistence layer** — Spring Data JPA repositories and entities.
5. **Database migration layer** — Flyway migrations for H2/MySQL environments.
6. **Operations layer** — Actuator, Micrometer/OpenTelemetry bridge, OTLP span exporter, Prometheus meter registry, Loki log appender, and request tracing interceptor.

## Security boundaries

- `/api/**` is matched by the JWT resource-server filter chain.
- Public documentation, root, actuator, and H2-console paths are explicitly permitted by configuration.
- Other MVC routes use OAuth2 login.
- CSRF is disabled in the configured HTTP chains because this application exposes API-style endpoints and OAuth2-protected routes; review this decision before production hardening.

## API documentation groups

| Group | Path pattern |
|---|---|
| `todo-kotlin` | `/api/todos/**` |
| `fruit-ordering` | `/api/fruit-ordering/**` |

The OpenAPI configuration declares bearer JWT authentication for documented APIs.

## Data flow

HTTP requests enter Spring MVC, pass through the applicable security chain and tracing interceptor, then reach a feature module. The module performs business logic, accesses JPA repositories, and persists to H2 or MySQL. Flyway initializes the selected schema. Domain/application events can be handled within the modular monolith through Spring Modulith. Request traceId and spanId enter the Micrometer MDC, are exported on the span to Tempo over OTLP, and are rendered into the log line sent to Loki, enabling bidirectional correlation between traces and logs in Grafana. In containerized environments, the Pyroscope Java agent continuously samples CPU using the itimer event and exports profiles to Pyroscope for analysis alongside traces, logs, and metrics. Allocation and lock profiling are available in the agent but are not enabled.

## External services

- **Auth0/OIDC:** JWT key retrieval and browser OAuth2 login.
- **MySQL:** containerized relational database for MySQL-oriented environments.
- **Grafana LGTM stack with profiling:** Prometheus (9090) scrapes application metrics; Tempo (3200, OTLP 4317/4318) collects traces pushed over OTLP HTTP; Loki (3100) receives logs pushed by the loki4j Logback appender; Pyroscope (4040) collects CPU and JFR profiling data via the Pyroscope Java agent. Grafana (3000) is the single UI with provisioned datasources for metrics, traces, logs, and profiles.

## Deployment topology

The application is packaged as a single JVM container image exposing port 8080. Docker Compose supplies supporting MySQL and the observability stack (Prometheus, Tempo, Loki, Grafana) for local or hosted environments. Configuration is injected through environment variables and Spring properties. In `docker-compose-full.yml`, the containerized app listens on 8088 internally and is published as 8080.
