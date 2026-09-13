# Phase 02 — Observability Stack Config Files

## Context Links

- [plan.md](plan.md)
- [research/researcher-05-verified-findings-synthesis.md](research/researcher-05-verified-findings-synthesis.md) — authoritative
- [research/researcher-03-tempo-loki-prometheus-configs.md](research/researcher-03-tempo-loki-prometheus-configs.md) (see corrections in -05)
- [research/researcher-04-grafana-datasource-provisioning.md](research/researcher-04-grafana-datasource-provisioning.md) (see corrections in -05)

## Overview

- **Priority:** P1 (blocks 03)
- **Status:** completed
- **Effort:** 45m
- Create the three mounted config files for Tempo, Prometheus and Grafana. Loki needs none.

## Key Insights

1. **Loki requires no config file.** `grafana/loki:3.7.7` bakes `/etc/loki/local-config.yaml` and
   its `CMD` already points at it (`auth_enabled: false`, `:3100`, filesystem storage,
   `schema: v13` + `tsdb`). Mounting a config would only add a maintenance burden and a way to
   break pushes. So only **three** files are created, not four.
2. **One Prometheus scrape job, two targets, one of them always down.** Mode A has the app on the
   host (`host.docker.internal:8080`); Mode B has it in the network (`backend:8088` — the
   container listens on 8088, per `SERVER_PORT=8088` at `docker-compose-full.yml:10`, published
   as 8080:8088). Prometheus scrapes both independently; a target being DOWN in the other mode is
   **expected and harmless** — it shows red on `/targets` and does not affect the live one.
3. **Grafana provisioning eats `$`.** `${...}` and `$VAR` are expanded as environment variables in
   provisioning files, so template tokens must be written `$${__value.raw}` and
   `$${__trace.traceId}`. Single-`$` produces a silently empty link — the single most likely cause
   of "correlation does nothing".
4. **Correlation direction Tempo -> Loki must use a LogQL line filter**, because traceId lives in
   the log *line* (not a label, not structured metadata). A stream selector is mandatory in LogQL,
   so `{app=~".+"}` is used to avoid hardcoding the app name.
5. Tempo receivers bind `0.0.0.0` rather than upstream's `tempo:4318`, so the config does not
   depend on the compose service name.

## Requirements

### Functional

- Tempo accepts OTLP on **both** 4318 (http) and 4317 (grpc); API/UI on 3200; local filesystem
  storage; no metrics-generator.
- Prometheus scrapes `/actuator/prometheus` on both run-mode targets.
- Grafana auto-provisions Prometheus, Tempo and Loki with stable UIDs and bidirectional
  trace<->log links.

### Non-functional

- No persistent volumes, no credentials, no alerting rules.
- Config must match the exact pinned image tags from phase 03.

## Architecture

```
docker/observability/
├── prometheus.yml           -> /etc/prometheus/prometheus.yml   (prom/prometheus:v3.14.0)
├── tempo.yaml               -> /etc/tempo.yaml                  (grafana/tempo:3.0.3)
└── grafana-datasources.yaml -> /etc/grafana/provisioning/datasources/datasources.yaml
                                                                 (grafana/grafana:13.2.1)
(no loki config — grafana/loki:3.7.7 uses its baked-in /etc/loki/local-config.yaml)
```

Correlation wiring:

```
Loki log line  [app,traceId,spanId]
      │ derivedFields regex captures traceId ──> Tempo datasource (uid: tempo)
      ▼
Tempo trace ── tracesToLogsV2 customQuery {app=~".+"} |= "traceId" ──> Loki datasource (uid: loki)
```

## Related Code Files

**Create**

- `docker/observability/prometheus.yml`
- `docker/observability/tempo.yaml`
- `docker/observability/grafana-datasources.yaml`

The `docker/` directory does not exist yet — create it.

**Modify / Delete** — none.

## Implementation Steps

### 1. `docker/observability/tempo.yaml`

Derived from the official single-binary example at tag `v3.0.3`, minus metrics-generator.

```yaml
stream_over_http_enabled: true

server:
  http_listen_port: 3200
  log_level: info

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

storage:
  trace:
    backend: local
    wal:
      path: /var/tempo/wal
    local:
      path: /var/tempo/blocks

usage_report:
  reporting_enabled: false
```

No `ingester:` / `compactor:` blocks — Tempo 3.x defaults are fine (they were required in 2.x).
The container must be launched with `-target=all` (phase 03).

### 2. `docker/observability/prometheus.yml`

```yaml
global:
  scrape_interval: 10s
  evaluation_interval: 10s

scrape_configs:
  - job_name: spring-modulith-kotlin
    metrics_path: /actuator/prometheus
    static_configs:
      # Mode A: app runs on the host via `gradlew bootRun` (server.port=8080).
      - targets: ['host.docker.internal:8080']
        labels:
          run_mode: host
      # Mode B: app runs as the `backend` container (SERVER_PORT=8088).
      - targets: ['backend:8088']
        labels:
          run_mode: container
```

Exactly one of these two targets is UP in any given run mode. That is intended; do not try to
"fix" the DOWN one. `host.docker.internal` resolves only because phase 03 adds
`extra_hosts: ['host.docker.internal:host-gateway']` to the prometheus service — the same trick
the removed `zipkin-all-in-one` service used (`docker-compose.yml:19`).

### 3. `docker/observability/grafana-datasources.yaml`

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    uid: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
    jsonData:
      httpMethod: GET

  - name: Tempo
    type: tempo
    uid: tempo
    access: proxy
    url: http://tempo:3200
    editable: false
    jsonData:
      httpMethod: GET
      tracesToLogsV2:
        datasourceUid: loki
        spanStartTimeShift: '-1h'
        spanEndTimeShift: '1h'
        tags: []
        filterByTraceID: false
        filterBySpanID: false
        customQuery: true
        query: '{app=~".+"} |= "$${__trace.traceId}"'

  - name: Loki
    type: loki
    uid: loki
    access: proxy
    url: http://loki:3100
    editable: false
    jsonData:
      maxLines: 1000
      derivedFields:
        - name: TraceID
          matcherRegex: '\[[^,\]]*,([0-9a-f]{32}),'
          url: '$${__value.raw}'
          urlDisplayLabel: 'View Trace'
          datasourceUid: tempo
```

Implementer notes — each of these is a silent-failure source:

- `$$` in `$${__trace.traceId}` and `$${__value.raw}` is **required**, not a typo.
- `matcherRegex` must stay **single-quoted** so YAML keeps the backslashes literal.
- The regex has exactly **one** capture group and requires 32 hex chars, so log lines with an
  empty traceId (`[app,,]`) correctly produce no link.
- The regex must stay in sync with phase 01's `<message><pattern>`.
- `tags: []` is ignored while `customQuery: true`; kept for explicitness.
- UIDs `prometheus` / `tempo` / `loki` are hardcoded so `datasourceUid` cross-references resolve.
- `exemplarTraceIdDestinations` is deliberately **not** configured (see Next Steps).

## Todo List

- [x] create `docker/observability/` directory
- [x] write `tempo.yaml` (OTLP http+grpc, local storage, no metrics-generator)
- [x] write `prometheus.yml` (one job, two targets, `/actuator/prometheus`)
- [x] write `grafana-datasources.yaml` (3 datasources, fixed UIDs)
- [x] verify `$$` escaping present in exactly two places
- [x] verify `matcherRegex` is single-quoted
- [x] lint all three files parse as YAML

## Success Criteria

- All three files parse as valid YAML.
- `grep -c '\$\${' docker/observability/grafana-datasources.yaml` returns `2`.
- No Loki config file exists under `docker/observability/`.
- After phase 03, Tempo starts without a config error and logs listeners on 4317 + 4318.
- Grafana -> Connections -> Data sources shows exactly three, all "editable: false", no errors.

## Risk Assessment

| # | Risk | L×I | Mitigation |
|---|---|---|---|
| R1 | `${...}` written with a single `$` => derived field / trace-to-log link resolves to empty, correlation silently dead | High×High | Explicit `$$` in step 3 + success criterion greps for exactly 2 occurrences |
| R2 | Derived-field regex does not match the actual shipped log line | Med×High | Regex authored against phase 01's exact `<message><pattern>`; phase 05 validates on real ingested logs before declaring done |
| R3 | Tempo 3.x config schema drift (2.x required `ingester`/`compactor`) | Low×High | Config lifted from the official `single-binary` example at the exact pinned tag `v3.0.3` |
| R4 | `host.docker.internal` unresolvable (Linux) => Mode A target DOWN | Med×Med | `extra_hosts: host-gateway` added in phase 03, reusing the pattern the old zipkin service already relied on |
| R5 | Renovate later bumps Tempo to a new major and the config stops parsing | Med×Med | Documented in phase 04; treat Tempo major bumps as config-migration PRs, not auto-merge |
| R6 | Loki rejects pushes due to structured metadata / schema | Low×Med | Baked-in config is `schema: v13` + `tsdb` which supports it; phase 01 sends no structured metadata |

## Security Considerations

- `grafana-datasources.yaml` contains **no** credentials — Loki/Tempo/Prometheus are all
  unauthenticated dev services. Nothing here is safe to expose off-localhost.
- Loki runs `auth_enabled: false` (baked default): any client can push/read any tenant. Dev only.
- `editable: false` prevents UI edits from drifting from the provisioned file.
- No secrets enter git; `secureJsonData` is intentionally absent.

## Next Steps

- Blocks phase 03 (mount paths + `-target=all` command).
- Runs in parallel with phase 01 (disjoint files).
- Follow-up (not this plan): Prometheus exemplars (`exemplarTraceIdDestinations`) for
  metric -> trace jumps; Tempo service graphs.
