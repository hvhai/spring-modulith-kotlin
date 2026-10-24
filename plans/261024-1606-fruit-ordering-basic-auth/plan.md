---
title: "Fruit Ordering Basic Auth"
description: "Protect the /fruit-ordering/** Thymeleaf UI with HTTP Basic (in-memory user from properties/env) via a dedicated filter chain; Auth0 stays for the rest of the app."
status: pending
priority: P1
effort: "3h"
tags: [security, basic-auth, fruit-ordering, ui]
created: 2026-10-24
---

# Fruit Ordering Basic Auth

Source: `plans/reports/brainstorm-261024-fruit-ordering-basic-auth.md`

## Overview

`/fruit-ordering/**` currently falls into `filterChainMvc` (Auth0 `oauth2Login`). Add a dedicated chain with `httpBasic` for that path only. Controller + navbar stop depending on `OidcUser`.

## Goals

| # | Goal | Priority |
|---|------|----------|
| 1 | `/fruit-ordering/**` requires HTTP Basic; unauthenticated gets 401 + `WWW-Authenticate: Basic` | P1 |
| 2 | Credentials come from properties/env, never hardcoded | P1 |
| 3 | `/`, `/api/**`, Auth0 login unchanged | P1 |

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Phase 1: Security chain, controller, navbar](./phase-01-start.md) | Pending |
| 2 | [Phase 2: Tests and docs](./phase-02-tests-and-docs.md) | Pending |

## Out of scope

REST `/api/fruit-ordering/**` auth, DB user store, removing Auth0, navbar redesign, logout semantics.

## Success Criteria

- [ ] `GET /fruit-ordering` without credentials -> 401 with `WWW-Authenticate: Basic`
- [ ] Valid credentials -> 200; create order and purchase work
- [ ] Wrong password -> 401
- [ ] `/` and `/api/**` behavior unchanged
- [ ] `./gradlew test` green

<!-- slug: fruit-ordering-basic-auth -->

## Validation Log

### Session 1 (2026-10-24)
Verification: Light tier, claims checked 8, verified 8, failed 0, unverified 0.

| Question | Decision |
|----------|----------|
| Missing FRUIT_ORDERING_* env | Fail-fast outside local; no default in `application.properties` |
| Password format | Plain in env, BCrypt-encoded at startup |
| Config location | New `FruitOrderingBasicAuthConfiguration` in `ui` package |

Propagated to phase-01 (files, steps, success criteria) and phase-02 (docs list, test props, risk).

### Whole-Plan Consistency Sweep
Re-read plan.md + both phases. Stale "SpringModulithKotlinApplication.kt gets the chain" and "extract if needed" wording replaced. Order values consistent: api=0, basic=1, mvc=2. Unresolved contradictions: 0.
