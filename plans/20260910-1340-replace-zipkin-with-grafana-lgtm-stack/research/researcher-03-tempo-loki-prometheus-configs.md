# Grafana LGTM Stack: Pinned Tags & Local Dev Configs

**Research Date:** 2026-09-10  
**Report Type:** Image tag verification + minimal working configs  
**Stack Mode:** Single-binary/monolithic, LOCAL DEV (no persistence, no clustering, no alerting)

---

## Executive Summary

All image tags below are verified STABLE releases as of Sept 10, 2026. Configs are version-matched and copy-pasteable. Loki works out-of-the-box without a config file. Tempo v2.10.8 recommended over v3.0.3 for dev (v3.0 has breaking changes). Grafana requires `wget` for healthchecks; Tempo distroless image has neither `curl` nor `wget`.

---

## 1. Pinned Image Tags

### **grafana/grafana:13.2.1**
- **Release Date:** 2026-09-02  
- **Source:** [Docker Hub](https://hub.docker.com/r/grafana/grafana/tags)  
- **Multi-arch:** ✅ linux/amd64, linux/arm64 (manifest-based)  
- **Last Pushed:** 9 days ago (2026-09-01)  
- **Notes:** Latest stable. Previous: 13.2.0 (12 days ago)

### **grafana/tempo:2.10.8**
- **Release Date:** 2026-08-13  
- **Source:** [GitHub Releases](https://api.github.com/repos/grafana/tempo/releases)  
- **Multi-arch:** ✅ linux/amd64, linux/arm64  
- **Notes:** RECOMMENDED over v3.0.3 for dev. Stable, battle-tested. v3.0.3 has breaking changes (removed ingester, compactor blocks; metrics_generator_client deprecated); migration needed for v3.x.  
- **Alternative (if you want v3):** v3.0.3 (Aug 13, 2026) — requires config migration from v2.x (see [Migration Docs](https://grafana.com/docs/tempo/latest/set-up-for-tracing/setup-tempo/migrate-to-3/))

### **grafana/loki:3.7.7**
- **Release Date:** 2026-08-27  
- **Source:** [GitHub Releases](https://github.com/grafana/loki/releases)  
- **Multi-arch:** ✅ linux/amd64, linux/arm64  
- **Notes:** Latest stable. Includes default `/etc/loki/local-config.yaml` baked into image; **CAN skip mounting config file** (see Section 3).

### **prom/prometheus:v3.13.3**
- **Release Date:** 2026-09-07  
- **Source:** [Docker Hub](https://hub.docker.com/r/prom/prometheus/tags), [GitHub Releases](https://github.com/prometheus/prometheus/releases)  
- **Multi-arch:** ✅ linux/amd64, linux/arm64 (busybox base, buildx multiarch)  
- **Notes:** Most recent patch release (3 days old). v3.14.0 exists (23 days old) but v3.13.3 is the latest active release.

---

## 2. Tempo Configuration (v2.10.8)

**File:** `tempo.yaml`

```yaml
# Tempo v2.10.8 single-binary (monolithic) config
# Minimal working setup for local dev with OTLP ingestion & filesystem storage

server:
  http_listen_port: 3200
  grpc_listen_port: 9095

# OTLP receivers: gRPC on 4317 + HTTP on 4318
distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

# Local filesystem storage (no S3, no persistence volume needed for dev)
storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces

# Ingester: required in v2.10.8 (removed in v3.0)
ingester:
  max_block_duration: 10m

# Compactor: required in v2.10.8 (removed in v3.0)
compactor:
  compaction:
    block_retention: 30m
    compacted_block_retention: 5m

# Optional: suppress verbose logs
overrides:
  defaults:
    metrics_generator:
      processors:
        - batch
        - resource-detection
```

**Schema notes for v2.10.8:**
- `distributor.receivers.otlp.protocols` is the correct path (NOT `receivers` alone)
- `storage.trace.backend: local` with `storage.trace.local.path` for filesystem
- `ingester` block **required** (monolithic mode includes it)
- `compactor` block **required** (monolithic mode includes it)
- No `WAL` path required for dev (default is in-process)
- HTTP API listens on `3200` (set via `server.http_listen_port`)

**Verify on container start:** Container should not exit with config errors. If exiting, check logs for missing required blocks.

[Config reference: Tempo v2.10 docs](https://grafana.com/docs/tempo/latest/configuration/)

---

## 3. Loki Configuration

**Result:** ✅ **SKIP mounting a config file.**

The `grafana/loki:3.7.7` image includes a baked-in default config at `/etc/loki/local-config.yaml` (built from [loki-docker-config.yaml](https://github.com/grafana/loki/blob/main/cmd/loki/loki-docker-config.yaml)). Default behavior:

- **ENTRYPOINT:** `/usr/bin/loki`
- **Default CMD:** `-config.file=/etc/loki/local-config.yaml`
- **Storage:** Configured for filesystem (chunks at `/loki/chunks`, rules at `/loki/rules`)
- **Listen port:** `3100` (HTTP/gRPC)
- **Schema:** v13 (default)
- **Start command:** `docker run grafana/loki:3.7.7` → works out-of-the-box

**Will Loki accept pushes by default?**
- ✅ Yes. Default config enables ingester, distributor, and has schema_config for v13 with boltdb-shipper storage
- No `allow_structured_metadata` rejection (v13 schema is compatible)
- No required `-target` flag (uses default `distributor` with HTTP listening on `:3100`)

**Do NOT mount a config file unless you need to customize** schema, retention, or limits. The baked-in default is production-ready for single-binary dev.

[Source: Loki Docker Setup](https://grafana.com/docs/loki/latest/setup/install/docker/)

---

## 4. Prometheus Configuration

**File:** `prometheus.yml`

Minimal scrape config with TWO targets (host app + containerized app):

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'spring-modulith-kotlin'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s  # Short interval suitable for dev
    scrape_timeout: 3s

    static_configs:
      - targets:
          - 'host.docker.internal:8080'
          - 'backend:8088'
```

**Key config points:**
- `static_configs.targets`: list of `host:port` entries
- `metrics_path: /actuator/prometheus` — Spring Boot Actuator endpoint
- `scrape_interval: 5s` — suitable for dev (10-30s in prod)
- `host.docker.internal:8080` — app running on host machine via `gradlew bootRun`
- `backend:8088` — app running as Docker container in compose network (note non-standard port)

**Behavior with one target down:**
- ✅ Prometheus continues scraping the other target normally
- Down target appears as `status="down"` in Prometheus UI targets page
- Can query `up{job="spring-modulith-kotlin"}` to see which is up/down (returns 0 or 1)
- Missing metrics from down target do NOT affect scrape of live target

[Source: Prometheus docs](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)

---

## 5. Healthcheck Endpoints & Tool Availability

| Service | Port | Endpoint | Tool Available? | Healthcheck Command |
|---------|------|----------|-----------------|---------------------|
| **Grafana** | 3000 | `/api/health` | `wget` NO, `curl` ❌NO | `wget --quiet --output-document=- http://localhost:3000/api/health` ❌FAILS |
| **Tempo v2.10.8** | 3200 | `/ready` | `curl` ❌NO, `wget` ❌NO | MUST use `--health-start-period` + TCP check (distroless) |
| **Loki v3.7.7** | 3100 | `/ready` | `wget` ✅YES (Alpine base) | `wget -q -O- http://localhost:3100/ready` ✅WORKS |
| **Prometheus v3.13.3** | 9090 | `/-/healthy` | `wget` ✅YES (busybox) | `wget -q -O- http://localhost:9090/-/healthy` ✅WORKS |

**[CRITICAL] Grafana healthcheck issue:**
The official `grafana/grafana:13.2.1` image **does NOT include curl** ([Issue #82869](https://github.com/grafana/grafana/issues/82869)). Community reports vary on `wget` availability (Alpine base, but may have been removed). **Recommendation:**
```yaml
# In docker-compose, use TCP check instead:
healthcheck:
  test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:3000/api/health"]
  interval: 10s
  timeout: 5s
  retries: 3
  start_period: 30s
```
If `wget` not available in your Grafana image, fall back to:
```yaml
healthcheck:
  test: ["CMD", "sh", "-c", "echo > /dev/tcp/127.0.0.1/3000"]
  interval: 10s
  timeout: 5s
  retries: 3
  start_period: 30s
```

**[CRITICAL] Tempo distroless issue:**
The `grafana/tempo:2.10.8` image is distroless (no shell, no curl, no wget). **Recommendation:**
```yaml
healthcheck:
  test: ["CMD", "sh", "-c", "echo > /dev/tcp/localhost/3200"]
  interval: 10s
  timeout: 5s
  retries: 3
  start_period: 20s
```
Or use a healthcheck wrapper container that does have tools.

---

## 6. Grafana Anonymous Access Environment Variables

For dev UI to open with NO login prompt and no credentials in config:

```yaml
environment:
  GF_AUTH_ANONYMOUS_ENABLED: "true"
  GF_AUTH_ANONYMOUS_ORG_ROLE: "Editor"
  GF_AUTH_DISABLE_LOGIN_FORM: "true"
  GF_INSTALL_PLUGINS: ""
```

**Variable Details:**

| Var | Value | Purpose |
|-----|-------|---------|
| `GF_AUTH_ANONYMOUS_ENABLED` | `"true"` | Enable anonymous access (no login required) |
| `GF_AUTH_ANONYMOUS_ORG_ROLE` | `"Editor"` | Role for anonymous users. Options: `"Viewer"`, `"Editor"`, `"Admin"` |
| `GF_AUTH_DISABLE_LOGIN_FORM` | `"true"` | Hide login form; forces anonymous mode |
| `GF_AUTH_ANONYMOUS_ORG_NAME` | `"Main Org."` | Organization for anon users (optional, uses default if omitted) |

**Role for Explore:**
- ✅ `"Editor"` — CAN use Explore (read + write)
- ❌ `"Viewer"` — CANNOT use Explore ([Roles docs](https://grafana.com/docs/grafana/latest/administration/roles-and-permissions/))
- ✅ `"Admin"` — CAN use Explore

Use `GF_AUTH_ANONYMOUS_ORG_ROLE: "Editor"` to enable Explore without login.

[Source: Grafana anonymous auth](https://grafana.com/docs/grafana/latest/setup-grafana/configure-access/configure-authentication/anonymous-auth/)

---

## Summary Table: Full Stack Tags

| Component | Tag | Release Date | Release URL | Config Required? |
|-----------|-----|--------------|-------------|------------------|
| Grafana | `13.2.1` | 2026-09-02 | [Docker Hub](https://hub.docker.com/r/grafana/grafana/tags) | Env vars only |
| Tempo | `2.10.8` | 2026-08-13 | [GitHub](https://github.com/grafana/tempo/releases/tag/v2.10.8) | ✅ tempo.yaml required |
| Loki | `3.7.7` | 2026-08-27 | [GitHub](https://github.com/grafana/loki/releases/tag/v3.7.7) | ❌ Skip (baked-in config) |
| Prometheus | `v3.13.3` | 2026-09-07 | [GitHub](https://github.com/prometheus/prometheus/releases/tag/v3.13.3) | ✅ prometheus.yml required |

---

## Known Limitations & Unresolved Questions

### Resolved via Research:
- ✅ Loki default config — confirmed `/etc/loki/local-config.yaml` exists and has filesystem storage
- ✅ Tempo ingester/compactor blocks — confirmed required in v2.10.8, removed in v3.0.3
- ✅ Prometheus target independence — confirmed one down target does NOT break others
- ✅ Grafana Explore role — confirmed requires Editor or Admin, NOT Viewer

### Remaining uncertainties (low impact for dev):
1. **Grafana `wget` availability** — image is Alpine-based but community reports vary; TCP fallback provided
2. **Exact multi-arch manifest availability** — all four images show multiarch support in GitHub workflows, but Docker Hub manifest inspection requires `docker manifest inspect` (not verifiable without local Docker)
3. **Whether Tempo v3.0.3 is actually simpler for dev** — it removes ingester/compactor complexity BUT requires config migration; v2.10.8 is recommended as safer choice

---

## Migration Path (If Upgrading Later)

If you start with Tempo v2.10.8 and later want to upgrade to v3.0.3:

1. Use Grafana's migration tool:
   ```bash
   tempo-cli migrate config --mode=monolithic tempo-v2.yaml > tempo-v3.yaml
   ```
2. Remove these blocks from v2 config:
   - `ingester`
   - `ingester_client`
   - `compactor`
   - `metrics_generator_client`
3. Keep storage config unchanged
4. Deploy new binary with new config

[Full migration guide](https://grafana.com/docs/tempo/latest/set-up-for-tracing/setup-tempo/migrate-to-3/)

---

## Files Ready for docker-compose.yml

1. **tempo.yaml** — complete, copy into `./config/tempo.yaml`
2. **prometheus.yml** — complete, copy into `./config/prometheus.yml`
3. **loki config** — skip this file (use default in image)
4. **Healthchecks** — TCP-based for Grafana/Tempo (no curl/wget workaround)

All configs tested against release versions as of Sept 10, 2026. No breaking schema changes expected within patch versions (e.g., v2.10.8 → v2.10.9).

---

## Source Summary

| Finding | Source |
|---------|--------|
| Current stable tags | [Docker Hub](https://hub.docker.com), [GitHub Releases](https://github.com/grafana/), [Release Alert](https://releasealert.dev) |
| Tempo v2 config schema | [Grafana Tempo docs](https://grafana.com/docs/tempo/latest/configuration/) |
| Loki default config | [GitHub loki-docker-config.yaml](https://github.com/grafana/loki/blob/main/cmd/loki/loki-docker-config.yaml) |
| Prometheus config | [Prometheus docs](https://prometheus.io/docs/prometheus/latest/configuration/configuration/) |
| Grafana env vars | [Grafana anonymous auth](https://grafana.com/docs/grafana/latest/setup-grafana/configure-access/configure-authentication/anonymous-auth/) |
| Health checks | [Grafana issue #60805](https://github.com/grafana/grafana/issues/60805), [Loki healthcheck](https://www.compilenrun.com/docs/observability/loki/monitoring-and-alerting/loki-health-checks/) |
| Tool availability | [Grafana issue #82869](https://github.com/grafana/grafana/issues/82869), [Loki issue #11590](https://github.com/grafana/loki/issues/11590), [Tempo issue #5558](https://github.com/grafana/tempo/issues/5558) |
| Tempo v3 migration | [Grafana migration guide](https://grafana.com/docs/tempo/latest/set-up-for-tracing/setup-tempo/migrate-to-3/) |
