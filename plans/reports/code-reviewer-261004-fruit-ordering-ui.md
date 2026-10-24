# Code Review: Fruit Ordering UI (uncommitted)

Scope: `ui/FruitOrderingUIController.kt` (134 LOC), `templates/fruit-ordering/{index,order-detail}.html`, `templates/fragments/order-status-badge.html`, `fragments/navbar.html` (+3 lines). Read-only; no build/test run (no shell available to reviewer).

## Verdict
Solid, scoped. No critical issues. One medium correctness/UX defect that contradicts a plan claim; rest low/informational.

## Medium

**M1. Purchase double-submit / stale page yields raw JSON 400, not flash error** — `FruitOrderingUIController.kt:117-123`
- Guard checks `order.orderStatus == WAITING_FOR_PURCHASE`. After a successful purchase, order status stays `WAITING_FOR_PURCHASE` until `PaymentEvent.PURCHASED` is consumed asynchronously (`fruitordering_order/internal/Business.kt:137-146`). A second POST in that window (double-click before redirect, second tab, back-button/bfcache page, or opening detail without `?purchasing=true`, which re-shows the button) passes the guard.
- `JpaPayment.purchase()` then does `require(purchaseAt == null)` (`fruitordering_payment/internal/Jpa.kt:33`) -> `IllegalArgumentException` -> global `@ControllerAdvice` (`share/ExceptionHandler.kt:43-47`) returns `@ResponseBody` JSON 400 to the browser.
- Phase-01 risk note ("double-submit after success hits flash error path, acceptable") is false.
- Concurrent double-click: no `@Version` on `JpaPayment`, both txns can pass `require` -> two `PURCHASED` events. Order `finish()` is idempotent, so only duplicate events/logs; pre-existing service weakness, now reachable from UI.
- Fix (UI-only, no module change): wrap the call:
  ```kotlin
  try { paymentService.purchasePayment(payment.id) }
  catch (e: IllegalArgumentException) {
      redirectAttributes.addFlashAttribute("error", "Payment already purchased")
      return "redirect:/fruit-ordering/orders/$id?purchasing=true"
  }
  ```
  Optionally disable the button on submit (`onsubmit="this.querySelector('button').disabled=true"`). Add a WebMvcTest case for it.

## Low

**L1. Unbounded `?purchasing=true` refresh** — `FruitOrderingUIController.kt:102,132-133`, `order-detail.html:40,47`. If the PURCHASED event fails/never lands (or user hits URL with `?purchasing=true` without having purchased), page reloads every 2s forever and the Purchase button is hidden. Acceptable per plan; consider a cap or a "still processing? reload without flag" link.

**L2. Index N+1 + unbounded list** — `index()` -> `OrderServiceImpl.allOrders()` (`Business.kt:32-35`) `findAll()` then `toDTO()` touches lazy `products` and inverse `@OneToOne payment` per order -> ~1+2N queries per page load, no paging. Pre-existing service; now hit on every visit and every redirect after validation errors. Non-blocking for demo scale.

**L3. No ownership check on order view/purchase** — any authenticated user can view/purchase any order by id. Orders carry no user (`createOrder` ignores `UserDTO`), REST API behaves the same, and per-user filtering is an explicit non-goal. Not an IDOR regression; payment id is correctly derived server-side (`:118`), so no cross-order payment purchase. Note for roadmap with existing CSRF-disabled risk.

## Checks passed (verified)
- (a) Acceptance criteria: navbar link (`navbar.html:34-36`); empty/unknown ids rejected before `createOrder` (`:71-81`); autoRefresh set matches plan (`:42-47,132-133`); Purchase form gated on status+payment+`!purchasing` (`order-detail.html:39-43`); CANCELED alert, no retry; badge mapping matches spec. Test/`check` criteria owned by tester, not verified here.
- (b) No regression: Index/Profile/Note controllers untouched; `/fruit-ordering/**` falls under MVC chain `anyRequest().authenticated()` (`SpringModulithKotlinApplication.kt:118-135`); `/api/**` untouched.
- (c) No API/schema/config/env changes.
- (d) Patterns: `layout:decorate`, `layout:fragment="content"`, `profile` model attr (via `@ModelAttribute`, same as `UIController`/`NoteController`), plain navbar hrefs like siblings.
- (e) Thymeleaf: enum compare via `.name()`, elvis `${x} ?: '-'`, `not purchasing` (Boolean attr always set), `#sets.size` on Kotlin `Set` (java.util.Set), `#lists.isEmpty`, nested ternary in `th:classappend`, fragment refs all valid. `#strings.substring(id,0,8)` safe: ids are UUID (`order/internal/Jpa.kt:71`). All user-visible data via `th:text` (no `utext`) -> no XSS. Disabled out-of-stock checkbox bypass only yields CANCELED order (validated id), harmless. Redirect handlers declare `RedirectAttributes`, so `profile` claims not leaked into redirect URL. Kotlin: nullable `preferredUsername ?: fullName` matches `UserDTO.username: String?`.

## Plan follow-ups
- Phases 1-2 look complete. Correct phase-01 risk note re double-submit after M1 fix.
- Phase 3: add test for purchase when payment already purchased.

## Unresolved Questions
- Accept M1 fix in controller (catch `IllegalArgumentException`) vs. leaving service-level JSON 400? Recommend controller catch (UI-only, no contract change).

Status: DONE_WITH_CONCERNS
Summary: Implementation meets plan ACs with correct Thymeleaf/Kotlin and no regressions or contract changes. One medium defect: repeat purchase POST before async status update returns raw JSON 400 instead of flash error.
Concerns/Blockers: M1 (non-blocking but should fix before ship); build/tests not run by reviewer.
