---
title: "Phase 2: Tests and docs"
status: todo
---

# Phase 2: Tests and docs

## Overview

Update UI controller tests to Basic auth, add auth-rejection cases, document the new config.

## Requirements

- [ ] Existing `FruitOrderingUIControllerTest` cases pass using Basic auth principal instead of `oidcLogin()`
- [ ] New cases: no credentials -> 401; wrong password -> 401

## Related Code Files

- Modify: `src/test/kotlin/com/codehunter/spring_modulith_kotlin/ui/FruitOrderingUIControllerTest.kt`
- Modify: `docs/deployment-guide.md` (env vars in Security considerations, HTTPS note)
- Modify: `docs/system-architecture.md` lines ~10-22 (Fruit Ordering pages now use HTTP Basic chain, not OAuth2-login session)
- Modify: `src/test/resources/application.properties` (test user/password so full-context tests start)

## Implementation Steps

1. `@WebMvcTest` slice does not load the app's `@Configuration` security by default: `@Import` the Basic-auth chain config `FruitOrderingBasicAuthConfiguration` (separate class, no Auth0 `@Value`) and set test properties for user/password.
2. Replace `oidcLoginAlice()` with `httpBasic("alice", "secret")` (`SecurityMockMvcRequestPostProcessors.httpBasic`) or `user("alice")`; update `UserDTO` expectations (`UserDTO("alice","alice")`).
3. Add `get_index_without_credentials_returns_401` and `wrong_password_returns_401`.
4. Run `./gradlew test --tests '*FruitOrderingUIControllerTest'`, then full `./gradlew test` (modulith module tests included).
5. Docs: add `FRUIT_ORDERING_USER` / `FRUIT_ORDERING_PASSWORD` and HTTPS recommendation.

## Success Criteria

- [ ] Focused test class green, full suite green
- [ ] Docs list new env vars

## Risk Assessment

- Auth0 `@Value` placeholders in the existing config class block slice tests; mitigated by the separate `FruitOrderingBasicAuthConfiguration`.
- Full-context/integration tests fail if test properties lack the new placeholders; run full suite to catch.
