# Project Overview and Product Development Requirements

## Overview

`spring-modulith-kotlin` is a Kotlin/Spring Boot reference application demonstrating a modular monolith with Spring Modulith. It combines a Todo capability with a fruit-ordering business flow and exposes both HTTP APIs and a server-rendered UI.

## Goals

1. Keep business capabilities isolated as application modules.
2. Provide authenticated REST endpoints and an OAuth2 login flow.
3. Persist data through JPA and version schema changes with Flyway.
4. Make local development observable with Actuator metrics, OTLP traces, and centralized logs through a Grafana LGTM stack.
5. Support H2-based application development and MySQL/container-based environments.

## Functional requirements

- Expose Todo endpoints under `/api/todos/**`.
- Expose fruit-ordering endpoints under `/api/fruit-ordering/**`.
- Protect API requests with bearer JWT authentication, except explicitly public endpoints.
- Provide OpenAPI documentation and Swagger UI.
- Support the fruit-ordering flow documented by `doc/fruits-ordering-flow.png`.

## Non-functional requirements

- Java 21 runtime.
- JUnit 5-compatible test execution through Gradle.
- Database migrations must be repeatable across supported database environments.
- Modules should communicate through explicit interfaces/events rather than accidental internal coupling.
- Credentials must be supplied through environment variables, not committed to source control.

## Constraints and assumptions

- Auth0/OIDC configuration is environment-specific.
- MySQL and the observability stack (Prometheus, Tempo, Loki, Grafana) are supplied by Docker Compose.
- Some integration tests may require Docker/Testcontainers.

## Success criteria

- `./gradlew test` completes successfully in a configured development environment.
- The application starts on port 8080.
- API documentation is available through Springdoc endpoints.
- Module boundaries remain verifiable with Spring Modulith tests/verification.
