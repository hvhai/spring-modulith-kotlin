---
title: Provision Grafana dashboard and light theme
date: 2026-09-12
summary: Grafana now provisions the observability dashboard in both Compose modes with a light default for fresh anonymous sessions.
---

# Provision Grafana dashboard and light theme

## What happened
Implemented native Grafana file provisioning for the checked-in Spring Boot Observability dashboard in both Compose modes. The dashboard now references the repository datasource UIDs directly, and fresh anonymous sessions default to light mode.

## Decision
Kept Grafana 13.2.1, existing anonymous access, login-form behavior, and datasource provisioning unchanged. Used a polling dashboard provider to make bind-mounted dashboard updates discoverable.

## Verification
Validated both Compose files and dashboard JSON; ran isolated base and full runtime/API smoke tests; confirmed a fresh browser session rendered the theme-light class; and received a 9.5/10 fresh-context review with no blockers.

## Next steps
No follow-up is required for this completed plan.

> Historical work record — not durable authority. Prefer docs/specs/ADRs for current decisions.
