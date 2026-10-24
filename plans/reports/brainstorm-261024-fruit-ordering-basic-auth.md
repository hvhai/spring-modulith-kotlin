# Brainstorm: Basic auth for /fruit-ordering

## Problem
`/fruit-ordering/**` (Thymeleaf UI, `FruitOrderingUIController`) goes through Auth0 `oauth2Login` (`filterChainMvc`, Order(1)). Want HTTP Basic instead, for this path only.

## Decisions (user-approved)
- Scope: UI `/fruit-ordering/**` only. REST `/api/fruit-ordering/**` untouched.
- Auth0 stays for rest of app. Basic auth = separate filter chain on `/fruit-ordering/**`.
- Users: in-memory from properties/env (`fruit-ordering.basic-auth.username/password`), no hardcoded password in repo.

## Approaches
- A (chosen): dedicated `SecurityFilterChain` w/ `securityMatcher("/fruit-ordering/**")`, `httpBasic`, stateless, CSRF off (as existing). Controller drops `OidcUser`.
- B (rejected): add `httpBasic` to existing chain. Unauthed requests still redirect to Auth0 (entry point), no 401 popup.

## Touchpoints
- `SpringModulithKotlinApplication.kt`: new chain placed before `filterChainMvc`; fix Order clash (chain /api already Order(0)); `InMemoryUserDetailsManager` + `PasswordEncoder` beans.
- `FruitOrderingUIController.kt`: `@AuthenticationPrincipal OidcUser` -> `Authentication`; `UserDTO(auth.name, auth.name)`; `addProfile` supplies fake `profile` map (`name`, `picture`) for navbar.
- `templates/fragments/navbar.html`: reads `profile.get('picture')`/`name`; needs fallback for empty picture.
- `application.properties` / `application-local.properties`: new props.
- `FruitOrderingUIControllerTest`: replace OIDC mock w/ basic auth; add 401 no-creds + wrong-password cases.

## Risks
- Basic over plain HTTP = plaintext; require HTTPS in deploy.
- Logout/Login navbar buttons meaningless for basic auth (browser caches creds). Out of scope.
- Orders keyed by username, not Auth0 `sub`; old orders not linked to new user id.
- Working tree has unrelated uncommitted edits (oauth2Login defaultSuccessUrl, navbar login link). Do not touch.

## Out of scope
REST API auth, DB user store, removing Auth0, navbar redesign.

## Acceptance
- `GET /fruit-ordering` no creds -> 401 + `WWW-Authenticate: Basic`.
- Valid creds -> 200; create order + purchase work.
- `/`, `/api/**` behavior unchanged.
- Existing tests pass.
