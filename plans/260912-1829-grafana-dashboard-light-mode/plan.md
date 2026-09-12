---
title: "Provision Grafana Dashboard and Light Theme"
description: "Provision the repo-managed Spring Boot Observability dashboard in both Compose modes and default anonymous Grafana sessions to light mode."
status: completed
priority: P2
effort: 1.5h
branch: feat/grafana-lgtm-observability
tags: [observability, grafana, docker-compose, docs]
blockedBy: []
blocks: []
created: 2026-09-12
---

# Provision Grafana Dashboard and Light Theme

## Overview

Implement native Grafana file provisioning for the existing dashboard JSON and add `GF_USERS_DEFAULT_THEME=light` in both Compose entrypoints. Keep `grafana/grafana:13.2.1`, current anonymous auth, and existing datasource provisioning unchanged.

## Evidence

- `docker-compose.yml:59` and `docker-compose-full.yml:82` define Grafana with image `grafana/grafana:13.2.1`.
- `docker-compose.yml:63` and `docker-compose-full.yml:86` enable anonymous access; login form stays disabled at `docker-compose.yml:65` and `docker-compose-full.yml:88`.
- Both Compose files currently mount only `grafana-datasources.yaml` for Grafana provisioning at `docker-compose.yml:67` and `docker-compose-full.yml:90`.
- Datasource UIDs are fixed in `docker/observability/grafana-datasources.yaml:6`, `:16`, `:34`, and `:49`.
- Dashboard title and UID already match at `docker/observability/25359_rev2.json:3515` and `:3516`; unresolved `__inputs` and `${DS_*}` references remain at `:2` and examples including `:162`, `:219`, `:824`, and `:3163`.

## Phases

| # | Phase | Status | Effort |
|---|---|---|---|
| 01 | [Provision dashboard and light theme](./phase-01-provision-grafana-dashboard-and-light-theme.md) | Completed | 1.5h |

## Data Flow

Compose mounts datasource YAML, provider YAML, and the dashboard JSON into Grafana. On startup, Grafana reads `/etc/grafana/provisioning/datasources/datasources.yaml`, reads `/etc/grafana/provisioning/dashboards/dashboards.yaml`, imports `/var/lib/grafana/dashboards/25359_rev2.json`, and anonymous browser sessions render the provisioned dashboard with the default light theme.

## Dependencies

- No cross-plan dependencies.
- The dashboard provider file must exist before both Compose files mount it.
- Dashboard datasource UIDs must be normalized before runtime verification.

## Success Criteria

- [x] `docker compose config` passes.
- [x] `docker compose -f docker-compose-full.yml config` passes.
- [x] `25359_rev2.json` parses as JSON and contains no `${DS_*}` references.
- [x] Grafana logs show no datasource or dashboard provisioning errors.
- [x] `GET /api/search` finds UID `spring-boot-observability`.
- [x] `GET /api/dashboards/uid/spring-boot-observability` returns resolved datasource UIDs.
- [x] A fresh anonymous browser session renders Grafana in light mode.

## Validation Log

### Verification Results — 2026-09-12

- **Tier:** Light (one phase); **claims checked:** 5; **verified:** 5; **failed:** 0; **unverified:** 0.
- Verified both Grafana service definitions, the pinned image, anonymous-auth settings, and current datasource-only mounts in `docker-compose.yml:59-67` and `docker-compose-full.yml:82-90`.
- Verified datasource UIDs in `docker/observability/grafana-datasources.yaml:6`, `:16`, `:34`, and `:49`.
- Verified dashboard metadata and unresolved import placeholders in `docker/observability/25359_rev2.json`.
- **Questions asked:** 0; the approved brainstorm resolves every material implementation decision.
- **Whole-plan consistency sweep:** both plan files reread; no stale decisions or unresolved contradictions.

<!-- slug: grafana-dashboard-light-mode -->
