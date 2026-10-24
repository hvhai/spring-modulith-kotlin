---
title: "Fruit Ordering UI"
description: "Thymeleaf SSR pages in the ui module for browsing products, placing orders, tracking status, and purchasing, built on existing fruit-ordering module services."
status: completed
priority: P1
effort: "5h"
branch: main
tags: [ui, thymeleaf, fruit-ordering, spring-modulith]
created: 2026-10-04
---

# Fruit Ordering UI

## Overview

Logged-in user opens "Fruit Ordering" from navbar → sees product catalog + order list → selects products and places an order → order detail page auto-refreshes through async status flow → clicks Purchase at `WAITING_FOR_PURCHASE` → order reaches `DONE`.

Source decision: [brainstorm report](../reports/brainstorm-261004-fruit-ordering-ui.md) (Approach A — Thymeleaf SSR, controller calls public module services directly).

## Contract

- **Constraints:** no REST API change; no security config change; Spring Modulith boundaries intact (`ModulithTest` green); reuse `layouts/default.html` + Bootstrap 4; no frontend build toolchain.
- **Non-goals:** per-product quantity (order products are a `Set`), per-user order filtering, SSE/realtime push, product admin, Todo/Note changes, fixing dead param in `JpaOrder.waitingForPayment`.

## Phases

| # | Phase | Status | Effort |
|---|-------|--------|--------|
| 1 | [UI controller](./phase-01-fruit-ordering-ui-controller.md) | Completed | 1.5h |
| 2 | [Templates and navbar](./phase-02-fruit-ordering-templates-and-navbar.md) | Completed | 1.5h |
| 3 | [Tests and docs](./phase-03-tests-and-docs.md) | Completed | 2h |

Dependencies: 1 → 2 → 3 (sequential; 2 renders model attributes defined in 1).

## Routes (all under MVC OAuth2-login chain, authenticated)

| Method | Path | Behavior |
|--------|------|----------|
| GET | `/fruit-ordering` | catalog (checkbox form) + all orders table |
| POST | `/fruit-ordering/orders` | form `productIds`; validate ≥1 and ⊆ catalog → `createOrder` → redirect detail |
| GET | `/fruit-ordering/orders/{id}` | detail; auto-reload while non-terminal |
| POST | `/fruit-ordering/orders/{id}/purchase` | server derives payment id from order; only at `WAITING_FOR_PURCHASE` → `purchasePayment` → redirect detail `?purchasing=true` |

## Success Criteria

- [x] Navbar shows "Fruit Ordering" link → `/fruit-ordering`
- [x] Order can be created from UI; empty/unknown product selection rejected with message, no service call
- [x] Detail page reloads while status in `PENDING|IN_PRODUCT_PREPARE|IN_PAYMENT_REQUESTED|CANCELING`, or `WAITING_FOR_PURCHASE` with `purchasing=true`; stops at `DONE|CANCELED`
- [x] Purchase button only at `WAITING_FOR_PURCHASE` with non-null `payment`; POST derives payment id server-side
- [x] `CANCELED` shown clearly, no retry button
- [x] `@WebMvcTest` for new controller green (15/15); `ModulithTest` green
- [ ] `./gradlew check` green — 34/35; only failure is pre-existing `FruitOrderingIntegrationTest.when create new order` (PaymentEvent CREATED never arrives in 10s; fails 2/2 on clean `main` too). Docker 29 needs `DOCKER_API_VERSION=1.44` for current Testcontainers.
- [x] Integration assertion: `OrderService.getOrder(id).payment?.id` non-null at `WAITING_FOR_PURCHASE`

## Risks

- CSRF disabled on MVC chain (existing) → new POSTs are CSRF-able in theory; mitigated by browser default `SameSite=Lax` on session cookie. Out of scope (security config change); noted for roadmap.
- `allOrders`/`getOrder` not `@Transactional`; lazy `payment`/`products` rely on default `spring.jpa.open-in-view=true` during MVC request. Don't disable OSIV.
- Manual E2E needs Auth0 creds in env; if unavailable, rely on WebMvcTest + integration test.

## Open Questions

None.

<!-- slug: fruit-ordering-ui -->
