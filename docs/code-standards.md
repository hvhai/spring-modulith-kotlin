# Code Standards

## Language and build

- Use Kotlin 1.9.25 and Java 21.
- Use the Gradle Kotlin DSL in `build.gradle.kts`.
- Run tests on the JUnit Platform.
- Prefer the repository Gradle wrapper over a system Gradle installation.

## Package and module organization

- Keep feature code under the application base package `com.codehunter.spring_modulith_kotlin`.
- Organize business capabilities as Spring Modulith application modules.
- Keep module internals package-private by convention where possible; expose only required application interfaces.
- Keep shared infrastructure configuration separate from business modules.

## Kotlin conventions

- Prefer immutable values (`val`) and constructor injection.
- Use Kotlin null-safety rather than unchecked nullable access.
- Use data classes for transport models/value-like structures where appropriate.
- Follow Spring/Kotlin conventions for JPA entities and use the configured `allOpen` plugin annotations.
- Keep configuration properties externalized through Spring configuration/environment variables.

## Security and configuration

- Do not commit client secrets, passwords, tokens, or private keys.
- Keep public security matchers explicit.
- Use bearer JWT validation for `/api/**` and OAuth2 login for browser routes as configured.
- Avoid exposing sensitive actuator environment values outside controlled development environments.

## Persistence

- Apply schema changes through Flyway migrations.
- Keep database-specific migrations/configuration clearly separated.
- Prefer repository/service boundaries over direct persistence access from web controllers.

## Testing

- Name tests after the behavior or component under test.
- Cover successful and failure paths, security rules, persistence behavior, and module boundaries.
- Use MockK/Mockito only at true external boundaries; prefer realistic Spring integration tests for wiring.
- Keep integration tests isolated and document Docker/Testcontainers prerequisites.

## Validation commands

```shell
./gradlew test
./gradlew check
```
