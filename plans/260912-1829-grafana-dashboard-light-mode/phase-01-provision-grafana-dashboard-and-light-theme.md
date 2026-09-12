---
phase: 1
title: "Provision dashboard and light theme"
status: pending
priority: P2
effort: 1.5h
dependencies: []
---

# Phase 1: Provision Dashboard and Light Theme

## Context Links

- [Plan overview](./plan.md)
- [Existing datasource provisioning](../../docker/observability/grafana-datasources.yaml)
- [Dashboard JSON](../../docker/observability/25359_rev2.json)
- [Grafana dashboard provisioning documentation](https://grafana.com/docs/grafana/latest/administration/provisioning/#dashboards)
- [Grafana Docker configuration documentation](https://grafana.com/docs/grafana/latest/setup-grafana/configure-docker/)

## Overview

- **Priority:** P2
- **Status:** pending
- **Effort:** 1.5h
- Add the smallest repo-managed provisioning path that works identically in both existing Compose modes.

## Goal

Grafana provisions the repo-managed `Spring Boot Observability` dashboard with UID `spring-boot-observability` in both Compose modes, and a fresh anonymous Grafana session opens in light mode.

## Key Insights

1. Grafana already provisions all four datasources with stable UIDs, so the dashboard should reference those UIDs directly instead of adding runtime import logic.
2. The dashboard's root `__inputs` block and `${DS_*}` values belong to the interactive import flow. Native file provisioning reads dashboard JSON from disk, so the repository copy must already contain resolvable datasource UIDs.
3. A 30-second provider interval forces polling rather than relying on filesystem events that may not propagate through Docker bind mounts.
4. `GF_USERS_DEFAULT_THEME=light` supplies a default, not an override for a browser or user with a saved preference; verification must use a fresh anonymous session.

## Requirements

- Keep `grafana/grafana:13.2.1` in `docker-compose.yml:60` and `docker-compose-full.yml:83`.
- Preserve anonymous Admin access and disabled login form from `docker-compose.yml:63-65` and `docker-compose-full.yml:86-88`.
- Preserve datasource provisioning and the datasource UIDs defined in `docker/observability/grafana-datasources.yaml:6`, `:16`, `:34`, and `:49`.
- Keep the dashboard JSON repo-managed at `docker/observability/25359_rev2.json`.
- Do not add Grafana persistence, homepage settings, auth changes, or dashboard query/layout redesigns.
- Update `docs/deployment-guide.md`, which already documents the stack at `docs/deployment-guide.md:8`, Grafana access at `:55`, and local-only security posture at `:119`.

## Files to Create / Modify

| Action | Absolute path | Purpose |
|---|---|---|
| Create | `C:\Users\LENOVO\works\projects\spring-modulith-kotlin\docker\observability\grafana-dashboards.yaml` | Grafana dashboard provider config. |
| Modify | `C:\Users\LENOVO\works\projects\spring-modulith-kotlin\docker-compose.yml` | Mount provider/dashboard and set light theme for infra-only mode. |
| Modify | `C:\Users\LENOVO\works\projects\spring-modulith-kotlin\docker-compose-full.yml` | Apply the same Grafana wiring for full-stack mode. |
| Modify | `C:\Users\LENOVO\works\projects\spring-modulith-kotlin\docker\observability\25359_rev2.json` | Remove import placeholders and normalize datasource UIDs. |
| Modify | `C:\Users\LENOVO\works\projects\spring-modulith-kotlin\docs\deployment-guide.md` | Add a minimal note for the provisioned dashboard and light default. |

File ownership is single-phase: this phase owns every file above. No parallel phase should edit these files.

## Architecture and Data Flow

1. Compose starts Grafana and mounts `grafana-datasources.yaml` into `/etc/grafana/provisioning/datasources/datasources.yaml`.
2. Compose also mounts `grafana-dashboards.yaml` into `/etc/grafana/provisioning/dashboards/dashboards.yaml`.
3. Compose mounts `25359_rev2.json` into `/var/lib/grafana/dashboards/25359_rev2.json`.
4. Grafana loads datasources first, then the dashboard provider imports JSON from `/var/lib/grafana/dashboards`.
5. Dashboard panels reference concrete datasource UIDs `prometheus`, `loki`, `tempo`, and `pyroscope`, matching the datasource provisioning file.
6. `GF_USERS_DEFAULT_THEME=light` sets the initial theme for users without a saved preference, including fresh anonymous sessions.

## Backward Compatibility

Existing Compose commands, ports, image tags, datasource names, datasource UIDs, and anonymous-auth behavior remain unchanged. Existing browser sessions may keep their saved Grafana theme preference; the guaranteed behavior is for fresh anonymous sessions without a saved preference.

## Implementation Steps

1. Create `grafana-dashboards.yaml` with:

   ```yaml
   apiVersion: 1

   providers:
     - name: Spring Boot Observability
       orgId: 1
       folder: ""
       type: file
       disableDeletion: false
       updateIntervalSeconds: 30
       allowUiUpdates: false
       options:
         path: /var/lib/grafana/dashboards
   ```

2. In both Compose files, add `GF_USERS_DEFAULT_THEME=light` beside the existing `GF_AUTH_*` environment lines.
3. In both Compose files, keep the existing datasource mount and add these read-only mounts under `grafana.volumes`:

   ```yaml
   - ./docker/observability/grafana-dashboards.yaml:/etc/grafana/provisioning/dashboards/dashboards.yaml:ro
   - ./docker/observability/25359_rev2.json:/var/lib/grafana/dashboards/25359_rev2.json:ro
   ```

4. Modify only the needed dashboard JSON fields. Remove the root `__inputs` array and replace datasource placeholder UIDs:

   | Replace | With |
   |---|---|
   | `${DS_PROMETHEUS}` | `prometheus` |
   | `${DS_LOKI}` | `loki` |
   | `${DS_TEMPO}` | `tempo` |
   | `${DS_PYROSCOPE}` | `pyroscope` |

5. Avoid whole-file JSON reformatting. Use targeted replacements or a structure-aware script that preserves most existing formatting.
6. Add a short `docs/deployment-guide.md` note near the local supporting services or observability section that Grafana auto-provisions the dashboard `Spring Boot Observability` and defaults new anonymous sessions to light mode.

## Verification

Run from `C:\Users\LENOVO\works\projects\spring-modulith-kotlin`:

```powershell
docker compose config
docker compose -f docker-compose-full.yml config
Get-Content -LiteralPath .\docker\observability\25359_rev2.json -Raw | ConvertFrom-Json | Out-Null
rg -n -F '${DS_' .\docker\observability\25359_rev2.json
```

The `rg` command must return exit code 1 with no matches.

For runtime smoke, first inspect port `3000` and the other published observability ports. If an unrelated process or another session owns a required port, do not kill it and do not choose a different port; report the blocked runtime check. Use deterministic verification-only Compose project names so cleanup cannot stop the user's default project.

```powershell
$verifyProject = 'smk-grafana-base-verify'
docker compose -p $verifyProject up -d grafana prometheus loki tempo pyroscope
# Poll http://localhost:3000/api/health until Grafana reports database=ok or a bounded timeout expires.
docker compose -p $verifyProject logs grafana --no-color
$search = Invoke-RestMethod 'http://localhost:3000/api/search?query=Spring%20Boot%20Observability'
if (-not ($search | Where-Object uid -eq 'spring-boot-observability')) { throw 'Provisioned dashboard not found' }
$dashboard = Invoke-RestMethod 'http://localhost:3000/api/dashboards/uid/spring-boot-observability'
$dashboardJson = $dashboard | ConvertTo-Json -Depth 100
if ($dashboardJson -match '\$\{DS_') { throw 'Dashboard still contains datasource placeholders' }
foreach ($uid in 'prometheus','loki','tempo','pyroscope') {
    if ($dashboardJson -notmatch ('"uid"\s*:\s*"' + [regex]::Escape($uid) + '"')) { throw "Missing datasource UID: $uid" }
}
docker compose -p $verifyProject down
```

Repeat with the full file only after the first verification project has been cleaned up:

```powershell
$verifyProject = 'smk-grafana-full-verify'
docker compose -p $verifyProject -f docker-compose-full.yml up -d grafana prometheus loki tempo pyroscope
# Repeat the bounded health, log, search, dashboard payload, and browser assertions above.
docker compose -p $verifyProject -f docker-compose-full.yml down
```

Wrap each runtime sequence in `try/finally` during implementation so its matching verification-only project is torn down even when an assertion fails. Never run `down` without the verification-specific `-p` value.

Manual browser check: open `http://localhost:3000` in a fresh private/incognito session and confirm the UI renders in light mode. Then open the dashboard and verify panels resolve Prometheus, Loki, Tempo, and Pyroscope datasources without "Datasource not found" errors.

## Test Matrix

| Layer | Command or check | Expected result |
|---|---|---|
| Compose syntax | `docker compose config` | Infra-only compose file is valid. |
| Compose syntax | `docker compose -f docker-compose-full.yml config` | Full-stack compose file is valid. |
| JSON syntax | `Get-Content -LiteralPath .\docker\observability\25359_rev2.json -Raw | ConvertFrom-Json | Out-Null` | Dashboard JSON parses. |
| Placeholder cleanup | `rg -n -F '${DS_' .\docker\observability\25359_rev2.json` | No matches; exit code 1. |
| Provisioning smoke | `docker compose logs grafana --no-color` for each mode | No datasource or dashboard provisioning errors. |
| API smoke | `Invoke-RestMethod http://localhost:3000/api/search` | Includes UID `spring-boot-observability`. |
| API smoke | `Invoke-RestMethod http://localhost:3000/api/dashboards/uid/spring-boot-observability` | Dashboard payload resolves concrete datasource UIDs. |
| Browser smoke | Fresh private/incognito session at `http://localhost:3000` | UI renders in light mode. |

## Success Criteria

- Compose config validation passes for both entrypoints.
- Dashboard JSON parses and has no `${DS_*}` placeholders.
- Grafana startup logs have no provisioning errors.
- Grafana API returns dashboard UID `spring-boot-observability`.
- Dashboard API payload contains datasource UIDs `prometheus`, `loki`, `tempo`, and `pyroscope`.
- Fresh anonymous browser session uses light mode.

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Dashboard JSON breaks while removing `__inputs`. | Medium | High | Parse JSON before and after; keep edit targeted; use rollback below. |
| Compose mounts point to wrong in-container paths. | Low | High | Use Grafana's provisioning directories and validate with logs plus API lookup. |
| Full-stack and infra-only compose files drift. | Medium | Medium | Apply identical Grafana environment and volume changes to both files, then run both config commands. |
| Light mode does not affect sessions with saved preferences. | Medium | Low | Acceptance is explicitly a fresh anonymous session; document this boundary. |
| Runtime smoke leaves containers running. | Low | Medium | Track which stack was started and run the matching `docker compose down`. |

## Security Considerations

This plan preserves the existing local-only security model: Grafana anonymous Admin access remains enabled, and the deployment guide already warns that the observability stack should not be exposed to untrusted networks. The new dashboard provisioning file and mounted JSON contain no credentials. Do not introduce secrets, tokens, or remote endpoints.

## Rollback

1. Remove the two new Grafana dashboard volume mounts from both Compose files.
2. Remove `GF_USERS_DEFAULT_THEME=light` from both Compose files.
3. Delete `docker/observability/grafana-dashboards.yaml`.
4. Reverse only this task's targeted edits in `docker/observability/25359_rev2.json`: restore the original `__inputs` block and `${DS_*}` values from the pre-change diff. Do not restore or overwrite the entire staged user-owned file.
5. Remove the deployment guide note.
6. Re-run both Compose config commands to confirm rollback syntax.

## Process Cleanup

Do not start duplicate stacks on port `3000`. Before runtime smoke, inspect port ownership and existing Compose projects. The verification commands use `smk-grafana-base-verify` and `smk-grafana-full-verify`; stop only those explicitly owned projects. If verification is interrupted, query those exact project names before cleanup and never kill an unrelated process.

## Todo

- [x] Create `grafana-dashboards.yaml`.
- [x] Update Grafana environment and mounts in `docker-compose.yml`.
- [x] Update Grafana environment and mounts in `docker-compose-full.yml`.
- [x] Normalize dashboard JSON datasource UIDs and remove `__inputs`.
- [x] Add the deployment guide note.
- [x] Run static and runtime verification for both Compose modes.

## Next Steps

- Execute this phase with `/ak:cook` after confirming the plan remains consistent with the working tree.
- Keep implementation changes scoped to the five listed product/configuration files; plan artifacts are tracking records only.
