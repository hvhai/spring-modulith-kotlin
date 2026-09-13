# Codebase Navigation

This repository demonstrates a Spring Modulith modular monolith with Todo, note, event-sourcing, and fruit-ordering capabilities. Product intent and constraints live in the [project overview](project-overview-pdr.md); this page points to executable owners.

| Concern | Executable owner |
|---|---|
| Application bootstrap, security, OpenAPI, CORS, and request JFR events | [`SpringModulithKotlinApplication.kt`](../src/main/kotlin/com/codehunter/spring_modulith_kotlin/SpringModulithKotlinApplication.kt) |
| Business modules and their public entry points | [`src/main/kotlin/com/codehunter/spring_modulith_kotlin/`](../src/main/kotlin/com/codehunter/spring_modulith_kotlin/) |
| Fruit-ordering business flow | [`doc/fruits-ordering-flow.png`](../doc/fruits-ordering-flow.png) |
| Runtime configuration and Spring profiles | [`src/main/resources/`](../src/main/resources/) |
| Database schemas | [H2 migrations](../src/main/resources/db/migration-h2/) and [MySQL migrations](../src/main/resources/db/migration/) |
| Dependencies, toolchain, and test task | [`build.gradle.kts`](../build.gradle.kts) and the [Gradle wrapper](../gradle/wrapper/gradle-wrapper.properties) |
| Unit, integration, security, and module-boundary tests | [`src/test/`](../src/test/) |
| Container image and run modes | [`Dockerfile`](../Dockerfile), [`docker-compose.yml`](../docker-compose.yml), and [`docker-compose-full.yml`](../docker-compose-full.yml) |
| Metrics, tracing, and Grafana datasource configuration | [`docker/observability/`](../docker/observability/) |
| Loki shipping | [`logback-spring.xml`](../src/main/resources/logback-spring.xml) |
| CI build and image publication | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) |

The [deployment guide](deployment-guide.md) owns the operating workflow and local-only security constraints. The [completed observability plan](../plans/20260910-1340-replace-zipkin-with-grafana-lgtm-stack/plan.md) retains migration decisions and verification evidence.
