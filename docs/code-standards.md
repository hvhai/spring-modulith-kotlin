# Code Standards

## Language and build

- Treat [`build.gradle.kts`](../build.gradle.kts) and the [Gradle wrapper properties](../gradle/wrapper/gradle-wrapper.properties) as the authority for language, framework, dependency, and build-tool versions.
- Prefer the repository Gradle wrapper over a system Gradle installation.

## Package and module organization

- Keep feature code under the application base package `com.codehunter.spring_modulith_kotlin`.
- Organize business capabilities as Spring Modulith application modules.
- Put module implementation details under an `internal` package and expose only required application interfaces from the module package.

## Kotlin conventions

- Prefer immutable values (`val`) and constructor injection.
- Use Kotlin null-safety rather than unchecked nullable access.
- Use data classes for transport models/value-like structures where appropriate.
- Follow the JPA openness configuration owned by [`build.gradle.kts`](../build.gradle.kts).
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

- Use the lowest reliable test layer for the application-owned behavior.
- Cover relevant success, failure, security, persistence, and module-boundary behavior without duplicating framework guarantees.
- Use test doubles at true external boundaries; use Spring integration tests when wiring or persistence is the contract.
- Keep integration-test data and infrastructure isolated. Tests that extend [`AppIntegrationBase`](../src/test/kotlin/com/codehunter/spring_modulith_kotlin/AppIntegrationBase.kt) require a working Docker environment for MySQL Testcontainers.

## Validation commands

```shell
./gradlew test
./gradlew check
```
