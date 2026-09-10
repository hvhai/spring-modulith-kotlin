# Project Roadmap

## Current baseline

- Spring Boot/Kotlin modular-monolith foundation exists.
- Todo and fruit-ordering API groups are configured.
- OAuth2/JWT security, JPA/Flyway persistence, OpenAPI, Actuator, and Grafana LGTM observability (Prometheus metrics, Tempo traces, Loki logs, Pyroscope profiles) are integrated.
- Continuous profiling via Pyroscope (2.1.2) Java agent is enabled in containerized deployments.
- Docker build and Docker Compose support are present.

## Near-term priorities

- Wire the Pyroscope `-javaagent` into `bootRun` Gradle task for Linux/macOS hosts so app-on-host mode can also profile.
- Document the concrete module/package map and public contracts.
- Make test database provisioning deterministic for local and CI runs.
- Add/maintain tests for API behavior, authorization rules, persistence, and module boundaries.
- Add CI validation for `clean check` and dependency/security checks.

## Medium-term priorities

- Harden production security configuration, especially actuator exposure, CORS, and CSRF decisions.
- Improve environment-specific configuration profiles for H2, MySQL, and production OAuth2.
- Publish API usage examples and operational runbooks.
- Restrict actuator exposure and remove or protect the Prometheus metrics endpoint before any non-local deployment.
- Investigate Prometheus exemplars to link metrics directly to traces.
- Remove the build-time `ARG`/`ENV` credential indirection from the `Dockerfile` so secrets cannot be baked into image layers.
- Treat Tempo and Loki major version upgrades as config-migration PR reviews, not zero-downtime auto-upgrades.

## Long-term possibilities

- Extract independently deployable services only if scale or ownership requires it.
- Add durable event delivery/retry monitoring for business events.
- Add contract and end-to-end tests around the complete fruit-ordering flow.

## Definition of done for roadmap work

- Automated tests cover the changed behavior.
- `./gradlew check` passes.
- Configuration and migration changes are documented.
- Security and observability implications are reviewed.
