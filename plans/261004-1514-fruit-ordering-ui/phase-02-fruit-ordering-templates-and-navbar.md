---
phase: 2
title: "Templates and navbar"
status: completed
priority: P1
effort: "1.5h"
dependencies: [1]
---

# Phase 2: Templates and navbar

## Overview
Two Thymeleaf pages decorated by `layouts/default` + navbar link.

## Requirements
- Functional:
  - `fruit-ordering/index.html`: flash `error` alert; product table (checkbox `name="productIds" value=id`, name, price, stock `quantity`; disable checkbox when `quantity == 0`); submit "Place order"; orders table (id short, status badge, total, product count, link to detail), newest-ish order irrelevant — keep service order.
  - `fruit-ordering/order-detail.html`: flash `error`; id, status badge, total, products list, payment id/amount if present; Purchase form (`POST /fruit-ordering/orders/{id}/purchase`) only when `orderStatus == WAITING_FOR_PURCHASE && payment != null && !purchasing`; "Processing…" text when `autoRefresh`; `CANCELED` → alert "Order canceled (out of stock)"; back link.
  - Auto reload: `<script th:if="${autoRefresh}">setTimeout(() => location.reload(), 2000)</script>` inside `layout:fragment="content"` (layout `<head>` is `th:replace`d from `fragments/header`, so don't rely on head merging for meta refresh).
  - Navbar: add `<li class="nav-item"><a href="/fruit-ordering" class="nav-link">Fruit Ordering</a></li>` after Note.
- Non-functional: Bootstrap 4 classes only; status badge map: `DONE`→success, `CANCELED`/`CANCELING`→danger, `WAITING_FOR_PURCHASE`→warning, else secondary.

## Architecture
Pages follow `note.html` pattern: `layout:decorate="~{layouts/default}"`, content in `layout:fragment="content"`. Forms use `th:action="@{...}"`.

## Related Code Files
- Create: `src/main/resources/templates/fruit-ordering/index.html`
- Create: `src/main/resources/templates/fruit-ordering/order-detail.html`
- Modify: `src/main/resources/templates/fragments/navbar.html`

## Implementation Steps
1. Create `index.html` with catalog form + orders table.
2. Create `order-detail.html` with status, products, payment, purchase form, reload script.
3. Add navbar link.
4. Run app locally (`.\gradlew.bat bootRun`, H2) if Auth0 env available; otherwise verify via Phase 3 MVC tests.

## Success Criteria
- [x] Both pages render with model from Phase 1 (verified by Phase 3 tests)
- [x] Navbar link visible on all pages

## Risk Assessment
- Thymeleaf SpEL on enum: compare with `${order.orderStatus.name() == 'DONE'}` to avoid type issues.
- Unauthenticated navbar render untouched (link visible but route requires login → OAuth2 redirect).
