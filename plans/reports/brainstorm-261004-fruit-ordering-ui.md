# Brainstorm: Fruit Ordering UI

Date: 2026-10-04 · Status: accepted, hand off to plan

## Contract
- **Outcome:** logged-in user opens Fruit Ordering page → views products → selects & places order → tracks order status → clicks Purchase when `WAITING_FOR_PURCHASE`.
- **Constraints:** no REST API change; keep Spring Modulith boundaries (`ModulithTest` green); reuse Thymeleaf layout dialect + Bootstrap 4 (`layouts/default.html`, `fragments/navbar.html`); no frontend build toolchain; no security config change.
- **Non-goals:** per-product quantity (order products are a `Set`), per-user order filtering, SSE/realtime push, product admin, Todo/Note UI changes.
- **Acceptance:** navbar "Fruit Ordering" link; full flow to `DONE` on H2; MVC tests for new pages; `ModulithTest` + `./gradlew check` green.

## Decision
**Approach A — Thymeleaf SSR.** New controller in `ui` module calls public module services directly:
- `WarehouseService.allProduct()` → product catalog
- `OrderService.createOrder(CreateOrderRequestDTO, UserDTO)` / `allOrders()` / `getOrder(id)`
- `PaymentService.purchasePayment(id)`
- `UserDTO` built from `OidcUser` (`sub`, `preferred_username`/`name`), NOT `AuthenticationUtil` (casts to `Jwt`).
- Order list shows all orders (user choice; matches `GET /orders`).
- Order detail auto-refreshes while status non-terminal (meta refresh or small JS reload); stops at `DONE`/`CANCELED`.
- POST-redirect-GET for create order & purchase.

Rejected:
- B (JS fetch + bearer token): needs Auth0 audience config, exposes access token to JS, `AuthenticationUtil` change.
- C (session auth on `/api/**`): CSRF disabled + CORS `*` → CSRF risk on POST endpoints; security contract change.

## Evidence
- APIs: `fruitordering_order/FruitOrderingOrderModuleExternal.kt`, `fruitordering_warehouse/FruitOrderingWarehouseModuleExternal.kt`, `fruitordering_payment/FruitOrderingPaymentModuleExternal.kt`
- Security chains: `SpringModulithKotlinApplication.kt:82-141` (`/api/**` JWT only; MVC routes OAuth2 login)
- `AuthenticationUtil` casts principal to `Jwt`: `Utils.kt:16`
- Async status flow via `@ApplicationModuleListener`: `fruitordering_order/internal/Business.kt:158-184`
- Orders have no owner column: `fruitordering_order/internal/Jpa.kt:67-93`

## Risks
- `ui` module gains deps on 3 fruit-ordering modules — verify `ModulithTest` allows (services live in module base packages → exposed).
- `createJpaOrder` silently drops unknown product ids; empty selection → order with no products, `registerForPayment` `reduce` on empty list throws. UI must require ≥1 product (server-side validation in controller).
- Out-of-stock path sets `CANCELED`; UI must show it, no retry.
- Purchase needs payment id from `OrderDTO.payment` — check it's populated at `WAITING_FOR_PURCHASE` (`waitingForPayment` passes `this.payment`, may be null → verify during implementation).

## Resolved: `OrderDTO.payment` at `WAITING_FOR_PURCHASE`
- Non-null on read. `JpaOrder.payment` is the inverse side (`mappedBy = "order"`, `Jpa.kt:79`); FK `order_id` lives on `fruit_order_payment`. `onPaymentCreatedEvent` saves `JpaOrderPayment(event.id, order, …)` first (`Business.kt:132`), so any later `getOrder` in a fresh persistence context loads it. `payment.id` = payment-module id (`event.id`) → valid input for `PaymentService.purchasePayment(id)`.
- `waitingForPayment(payment)` ignores its param (`payment = this.payment`, `Jpa.kt:135`) — dead param, harmless because inverse side doesn't own FK. Not in scope; optional one-line cleanup.
- Plan must include an MVC/integration test asserting the Purchase button renders with payment id at `WAITING_FOR_PURCHASE` (guards this assumption). No `allPayments()` fallback needed.

## Unresolved questions
- None blocking.
