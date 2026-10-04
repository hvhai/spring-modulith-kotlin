---
phase: 3
title: "Tests and docs"
status: completed
priority: P1
effort: "2h"
dependencies: [1, 2]
---

# Phase 3: Tests and docs

## Overview
MVC slice tests for the new controller + templates, one integration assertion guarding the `payment` non-null assumption, and minimal docs update.

## Requirements
- `@WebMvcTest(FruitOrderingUIController::class)` with `@MockkBean` for 3 services (pattern: `todo/TodoControllerTest.kt`), requests `.with(oidcLogin().idToken { it.subject("user-1").claim("preferred_username","alice") })`.
- Cases:
  1. GET `/fruit-ordering` → 200, view `fruit-ordering/index`, contains product names + order id; navbar contains "Fruit Ordering".
  2. POST orders with no `productIds` → 3xx to `/fruit-ordering`, flash `error`, `createOrder` never called.
  3. POST orders with unknown id → same as 2.
  4. POST orders valid → `createOrder(match { ids }, UserDTO("user-1","alice"))`, 3xx to `/fruit-ordering/orders/{id}`.
  5. GET detail `IN_PRODUCT_PREPARE` → reload script present, no purchase form.
  6. GET detail `WAITING_FOR_PURCHASE` + payment → purchase form present, no reload script.
  7. GET detail `WAITING_FOR_PURCHASE` + `purchasing=true` → reload, no purchase form.
  8. GET detail `DONE`/`CANCELED` → no reload; CANCELED alert.
  9. POST purchase at `WAITING_FOR_PURCHASE` → `purchasePayment(paymentId)` called, redirect `?purchasing=true`.
  10. POST purchase at other status → not called, flash error.
  11. GET detail unknown id (`IdNotFoundException`) → redirect `/fruit-ordering`.
  12. Unauthenticated GET `/fruit-ordering` → 3xx (login redirect).
- Integration: in `fruitordering/FruitOrderingIntegrationTest.kt`, add a test (or extend existing) that waits until order status `WAITING_FOR_PURCHASE` (`scenario...andWaitForStateChange { orderRepository.findAll().firstOrNull()?.orderStatus == WAITING_FOR_PURCHASE }`) then asserts `orderService.getOrder(id).payment?.id == paymentRepository.findByOrderId(id).first().id`. Requires Docker (Testcontainers MySQL).
- Docs: update `docs/codebase-summary.md` (ui module routes/deps) and `docs/system-architecture.md` only if they enumerate ui routes or module dependencies. Read first.

## Related Code Files
- Create: `src/test/kotlin/com/codehunter/spring_modulith_kotlin/ui/FruitOrderingUIControllerTest.kt`
- Modify: `src/test/kotlin/com/codehunter/spring_modulith_kotlin/fruitordering/FruitOrderingIntegrationTest.kt`
- Modify (conditional): `docs/codebase-summary.md`, `docs/system-architecture.md`

## Implementation Steps
1. Write WebMvcTest cases 1–12; run `.\gradlew.bat test --tests "*FruitOrderingUIControllerTest"`.
2. Add integration assertion; run `.\gradlew.bat test --tests "*FruitOrderingIntegrationTest"` (Docker up).
3. Run `.\gradlew.bat test --tests "*ModulithTest"` then `.\gradlew.bat check`.
4. Docs update; verify claims against code.

## Success Criteria
- [x] All 12 MVC cases green (plus purchase-retry case; 15/15)
- [ ] Integration assertion green — added inside existing `when create new order` test, which fails before reaching it (pre-existing, fails on clean `main`)
- [ ] `./gradlew check` green — 34/35, same pre-existing failure

## Risk Assessment
- `@WebMvcTest` loads `SpringModulithKotlinApplication` security beans; `TodoControllerTest` proves slice works. If OAuth2 client registration missing in slice, add test property or `@MockkBean ClientRegistrationRepository` — don't change main security config.
- Integration flake from async events → wait on state change, not on event arrival.
