---
phase: 1
title: "UI controller"
status: completed
priority: P1
effort: "1.5h"
dependencies: []
---

# Phase 1: UI controller

## Overview
New `@Controller` in `ui` module exposing 4 MVC routes over `WarehouseService`, `OrderService`, `PaymentService`. No change to fruit-ordering modules.

## Requirements
- Functional:
  - `GET /fruit-ordering` → model `products` (`warehouseService.allProduct()`), `orders` (`orderService.allOrders()`), view `fruit-ordering/index`.
  - `POST /fruit-ordering/orders` with `productIds: List<String>?` (form checkboxes):
    - empty/null → flash `error="Select at least one product"`, redirect `/fruit-ordering`, no `createOrder` call.
    - any id not in `allProduct()` ids → flash error, redirect, no call (`createJpaOrder` silently drops unknown ids; empty set crashes `registerForPayment`).
    - valid → `createOrder(CreateOrderRequestDTO(ids.map(::ProductRequestDTO)), userFrom(oidcUser))` → redirect `/fruit-ordering/orders/{id}`.
  - `GET /fruit-ordering/orders/{id}` → model `order`, `autoRefresh`, `purchasing`; view `fruit-ordering/order-detail`. `IdNotFoundException` → flash error, redirect `/fruit-ordering`.
  - `POST /fruit-ordering/orders/{id}/purchase` → `getOrder(id)`; if status `WAITING_FOR_PURCHASE` and `payment != null` → `paymentService.purchasePayment(payment.id)`, redirect `/fruit-ordering/orders/{id}?purchasing=true`; else flash error, redirect detail (no call).
  - All handlers add model `profile = oidcUser.claims` (navbar dereferences `profile.get('picture')` when authenticated) — use one `@ModelAttribute` method.
- Non-functional: POST-redirect-GET; no new dependencies; file < 200 LOC.

## Architecture
```
Browser ──(session, OAuth2 login chain)──> FruitOrderingUIController (ui)
   ├─ WarehouseService.allProduct()
   ├─ OrderService.createOrder/allOrders/getOrder
   └─ PaymentService.purchasePayment(order.payment.id)
Async: Order→Warehouse→Payment→Order events update status; detail page polls via reload.
```
`autoRefresh = status in {PENDING, IN_PRODUCT_PREPARE, IN_PAYMENT_REQUESTED, CANCELING} || (status == WAITING_FOR_PURCHASE && purchasing)`.
Reason for `purchasing` flag: after purchase, status stays `WAITING_FOR_PURCHASE` until `PaymentEvent.PURCHASED` is consumed; order-side `payment.purchaseAt` is never updated, so it can't signal progress.

User mapping: `UserDTO(id = oidcUser.subject, username = oidcUser.preferredUsername ?: oidcUser.fullName)`. Do NOT use `AuthenticationUtil` (casts principal to `Jwt`, `Utils.kt:16`).

## Related Code Files
- Create: `src/main/kotlin/com/codehunter/spring_modulith_kotlin/ui/FruitOrderingUIController.kt`
- Read: `fruitordering_*/FruitOrdering*ModuleExternal.kt`, `share/FruitOrderingSharedDTO.kt`, `share/CommonDTO.kt` (`UserDTO`, `IdNotFoundException`), `ui/UIModule.kt` (style reference)

## Implementation Steps
1. Create controller `@Controller @RequestMapping("/fruit-ordering")` with constructor-injected 3 services; logger like `UIController`.
2. `@ModelAttribute fun profile(model, @AuthenticationPrincipal oidcUser: OidcUser?)` adds `profile` when non-null.
3. Implement 4 handlers per requirements using `RedirectAttributes.addFlashAttribute("error", …)`.
4. Private `userFrom(OidcUser)` and `shouldAutoRefresh(OrderDTO, purchasing)` helpers.
5. `.\gradlew.bat compileKotlin` and run `ModulithTest` — confirm `ui → fruitordering_*` deps allowed (services live in module base packages = exposed API).

## Success Criteria
- [x] Compiles; `ModulithTest` green
- [x] No edits outside `ui/` package

## Risk Assessment
- Modulith violation if any `internal` type imported → only use base-package interfaces/DTOs.
- `purchasePayment` on already-purchased payment: status stays `WAITING_FOR_PURCHASE` until async PURCHASED event, so a repeat submit passes the status check and the payment entity throws `IllegalArgumentException` (global handler would return JSON 400). Controller catches it → flash error + redirect `?purchasing=true`.
- `?purchasing=true` reloads until DONE; if the PURCHASED event never lands, page reloads indefinitely (accepted, low).
