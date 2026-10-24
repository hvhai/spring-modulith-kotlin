---
title: "Phase 1: Security chain, controller, navbar"
status: todo
---

# Phase 1: Security chain, controller, navbar

## Overview

Dedicated Basic-auth filter chain for `/fruit-ordering/**`, in-memory user, controller and navbar decoupled from `OidcUser`.

## Requirements

- [ ] New `SecurityFilterChain` with `securityMatcher("/fruit-ordering/**")`, `httpBasic`, `anyRequest().authenticated()`, stateless, CSRF disabled (matches existing chains)
- [ ] Chain ordering explicit and non-clashing: today `filterChain` (`/api/**`) is `@Order(0)` and `filterChainMvc` is `@Order(1)`; new chain `@Order(1)`, `filterChainMvc` moves to `@Order(2)` (api=0, basic=1, mvc=2)
- [ ] `InMemoryUserDetailsManager` + `BCryptPasswordEncoder` encoding the plain env password at startup; username/password from `fruit-ordering.basic-auth.username` / `.password`, no default password committed
- [ ] Controller uses `Authentication` instead of `@AuthenticationPrincipal OidcUser`

## Related Code Files

- Create: `src/main/kotlin/com/codehunter/spring_modulith_kotlin/ui/FruitOrderingBasicAuthConfiguration.kt` (chain + `UserDetailsService` + `PasswordEncoder`)
- Modify: `src/main/kotlin/com/codehunter/spring_modulith_kotlin/SpringModulithKotlinApplication.kt` (only `@Order` values; do not disturb unrelated uncommitted `oauth2Login` edit)
- Modify: `src/main/kotlin/com/codehunter/spring_modulith_kotlin/ui/FruitOrderingUIController.kt`
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/main/resources/application.properties`, `application-local.properties`

## Implementation Steps

1. `application.properties`: `fruit-ordering.basic-auth.username=${FRUIT_ORDERING_USER}` and `.password=${FRUIT_ORDERING_PASSWORD}` with NO default (unresolved placeholder = startup failure outside local). Plain password in env; encoded in code. `application-local.properties` and `src/test/resources/application.properties` (and `application-integration.properties` if it loads full context) supply dev/test values.
2. New `FruitOrderingBasicAuthConfiguration` (ui package): `@Value` props, chain bean `@Order(1)` with `.securityMatcher("/fruit-ordering/**")`, `.httpBasic(withDefaults)`, `.sessionManagement { STATELESS }`, `.csrf { disable }`; `UserDetailsService` bean encodes the plain env password with BCrypt at startup. In `SpringModulithKotlinApplication.kt` only change `filterChainMvc` to `@Order(2)` (api stays 0).
3. Controller: `createOrder(@RequestParam ..., authentication: Authentication, ...)`; `userFrom(auth) = UserDTO(auth.name, auth.name)`. `addProfile` becomes `@ModelAttribute fun addProfile(model, authentication: Authentication?)` adding `profile = mapOf("name" to name, "picture" to "")`.
4. `navbar.html`: guard `<img th:src=...picture>` with `th:if="${!#strings.isEmpty(profile.get('picture'))}"` (both full-size and responsive blocks) so empty picture renders no broken image. Leave logout/login markup alone (out of scope).
5. Manual smoke: `curl -i localhost:8080/fruit-ordering` -> 401; `curl -u user:pass` -> 200.

## Success Criteria

- [ ] App starts with env vars set; fails fast (placeholder error) if unset outside local profile
- [ ] 401/200 behavior as above
- [ ] Auth0 login on other routes still works

## Risk Assessment

- Basic over plain HTTP is plaintext: require HTTPS in deploy (note in docs).
- Orders now keyed by username, not Auth0 `sub`; old orders not linked. Accepted.
- Unrelated uncommitted edits in the same file; edit surgically.
