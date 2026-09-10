# Codebase Summary

## Repository purpose

A Spring Modulith Kotlin application for demonstrating a modular monolith with Todo and fruit-ordering capabilities.

## Top-level structure

| Path | Purpose |
|---|---|
| `src/main/kotlin/` | Kotlin application and feature modules |
| `src/main/resources/` | Application configuration, templates, static files, and database migrations |
| `src/test/` | JUnit 5 unit, integration, security, and module tests |
| `doc/` | Product/flow documentation and images |
| `gradle/` | Gradle wrapper files |
| `build.gradle.kts` | Build plugins, dependencies, and test configuration |
| `docker-compose.yml` | MySQL and the Grafana LGTM observability stack |
| `docker/observability/` | Prometheus, Tempo, and Grafana datasource provisioning |
| `Dockerfile` | Multi-stage container build |

## Application entry point and shared configuration

The main application class is in package `com.codehunter.spring_modulith_kotlin`. It enables Spring Boot and configures:

- Two Spring Security filter chains: API bearer JWT security and MVC OAuth2 login.
- CORS and request tracing through a servlet interceptor.
- OpenAPI bearer authentication metadata and grouped API documentation.
- Actuator/tracing-related runtime configuration.

The `src/main/resources/logback-spring.xml` includes Spring Boot's logging defaults and gates the Loki log appender on the `local` and `docker` Spring profiles. Under other profiles (default, integration), logging remains console-only and does not require a running Loki instance.

## Feature areas

- **Todo:** documented by the OpenAPI group `todo-kotlin`.
- **Fruit ordering:** documented by the OpenAPI group `fruit-ordering`; the business flow is illustrated in `doc/fruits-ordering-flow.png`.
- **Persistence:** Spring Data JPA with Flyway migrations, H2 by default and MySQL/container support.

## Dependencies

Production dependencies cover MVC, Thymeleaf, OAuth2 client/resource server, Security, Spring Modulith Core/JPA/Insight, JPA, MySQL, H2, Flyway, Actuator, Micrometer tracing with the OpenTelemetry bridge and OTLP exporter, Micrometer Prometheus registry, loki4j Logback appender, Springdoc, CommonMark, and Apache HttpClient.

Test dependencies include Spring Boot Test, Kotlin/JUnit 5, MockK, Mockito Kotlin, Spring Security Test, Spring Modulith Test, WireMock, and Testcontainers with MySQL.

## Build and test behavior

The Gradle `Test` task uses JUnit Platform and logs passed, skipped, and failed tests with full exception details. It also prints a final test summary and failed test names.

## Known configuration considerations

- OAuth2 client values use `CLIENT_ID` and `CLIENT_SECRET`.
- The default application configuration uses an in-memory H2 database and H2 Flyway migrations.
- Test configuration switches to MySQL-oriented settings; integration tests may therefore need Docker or test-specific dynamic database configuration.
- The Dockerfile intentionally builds with `-x test`; run tests separately before producing an image.
