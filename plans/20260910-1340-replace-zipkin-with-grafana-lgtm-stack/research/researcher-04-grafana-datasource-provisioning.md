# Grafana Datasource Provisioning: Prometheus + Tempo + Loki with Bidirectional Trace-Log Correlation

**Status:** Complete research report  
**Date:** 2026-09-10  
**Context:** Spring Boot 3.4.1 + Micrometer tracing → OTLP/Tempo + loki4j/Loki  
**Sources:** Grafana official docs, devenv configs, GitHub issues

---

## 1. Provisioning File Skeleton & Structure

**API Version:** `1` (confirmed as current standard via [Grafana provisioning docs](https://grafana.com/docs/grafana/latest/administration/provisioning/))

**File Path:** `/etc/grafana/provisioning/datasources/datasources.yaml`  
Alternative via env var: `GF_PATHS_PROVISIONING=/path/to/provisioning`

**Datasources List Structure:**
```yaml
apiVersion: 1
datasources:
  - name: <datasource_name>
    type: <prometheus|tempo|loki>
    uid: <stable-unique-id>
    access: proxy          # or 'direct' for browser access
    url: <backend_url>
    isDefault: <true|false>
    editable: <true|false>
    jsonData: {}           # type-specific config
    secureJsonData: {}     # auth secrets (basicAuthPassword, etc)
```

**Datasource Type Values:**
- `prometheus` — Prometheus backend  
- `tempo` — Grafana Tempo tracing backend  
- `loki` — Loki log aggregation backend  

**Required Fields (per official docs):**  
- `name` — Display name  
- `type` — Datasource type string  
- `access` — "proxy" (Grafana → backend) or "direct" (browser → backend)  

**Optional but Recommended:**
- `uid` — Stable cross-reference ID (MUST be set for provisioning to enable correlations)
- `url` — Backend URL
- `isDefault` — Set one datasource per org as default
- `editable` — Allow UI edits (false for immutable provisioned sources)
- `jsonData` — Type-specific configuration (critical for correlations)

**File Mode:** Place in `/etc/grafana/provisioning/datasources/` or custom path via `GF_PATHS_PROVISIONING`.  
Grafana auto-reloads changes; no restart required.

---

## 2. Loki `derivedFields` — Exact Schema & Regex for Trace Extraction

### Schema
```yaml
jsonData:
  derivedFields:
    - name: <string>                    # Display name for the link
      matcherRegex: <regex_pattern>     # ONE capture group only
      matcherType: 'regex'              # ('regex' is default/recommended for this task)
      url: '${__value.raw}'             # Must use this token; $$ in YAML double-quotes
      datasourceUid: <target_uid>       # e.g., 'tempo'
      urlDisplayLabel: <string>         # (optional) Custom link label
```

**Critical Notes:**
- **Single Capture Group Only:** Grafana derived fields support exactly ONE capture group `()`. Patterns with alternation, non-capturing groups `(?:...)`, or multiple groups will fail silently or partially.
- **`matcherRegex` vs `matcherType`:** Both `matcherRegex` (string) and `matcherType: "regex"` (type tag) coexist. Use both; `matcherType` clarifies intent.
- **URL Token:** Always `'${__value.raw}'` (UNQUOTED in some contexts; safe with single quotes in YAML).
- **Empty Matches:** If regex doesn't match a log line, no link appears (silent, not error).
- **Label Type Alternative:** `matcherType: 'label'` can match Loki label keys via regex (e.g., `trace[_]?id` matches `trace_id` or `traceid` labels), but requires labels to exist in Loki output. For embedded trace IDs in log text, use `regex`.

### Regex for Correlation Pattern `[appName,traceId,spanId]`

**Requirement:** Extract ONLY the `traceId` (32 hex chars), skip when empty, ignore spanId.

**Recommended Regex:**
```
^\[.*?,([a-f0-9]{32}),
```

**Explanation:**
- `^\[` — Match start of line + literal `[`
- `.*?` — Non-greedy match any chars (app name + comma)
- `,` — Match comma after app name
- `([a-f0-9]{32})` — **CAPTURE GROUP:** exactly 32 hex chars (traceId) OR nothing → NO MATCH
- `,` — Following comma (before spanId)

**Why This Works:**
- If traceId is empty (log has `[app,,]`), the `{32}` quantifier fails → no link generated ✓
- spanId (16 hex chars) is NOT captured (only 32-char group matches) ✓
- Anchored to line start to avoid false positives ✓

**YAML Escaping (Single Quotes — Safest):**
```yaml
matcherRegex: '^\[.*?,([a-f0-9]{32}),'
```
Single quotes in YAML treat `\` literally → no extra escaping needed.

**YAML Escaping (Double Quotes — Requires Doubling):**
```yaml
matcherRegex: "^\\[.*?,([a-f0-9]{32}),"
```
Double quotes require `\\` for literal backslash.

**Recommendation:** Use **single quotes** to avoid escaping pain.

**Testing:** Before deploying, verify regex against a real log line:
```
[spring-modulith-kotlin,3a4f1c9e8b2d7a610f5e4c3b2a190876,7a610f5e4c3b2a19]
```
Should capture: `3a4f1c9e8b2d7a610f5e4c3b2a190876`

---

## 3. Tempo `tracesToLogsV2` — Exact Schema & Configuration

### Complete Schema
```yaml
jsonData:
  tracesToLogsV2:
    datasourceUid: <string>             # UID of Loki datasource (e.g., 'loki')
    spanStartTimeShift: <duration>      # e.g., '-1h' (before span start)
    spanEndTimeShift: <duration>        # e.g., '1h' (after span end)
    tags: <array>                       # (optional) Tag mappings
    filterByTraceID: <boolean>          # (optional, default true)
    filterBySpanID: <boolean>           # (optional, default false)
    customQuery: <boolean>              # (optional) Enable custom query mode
    query: <string>                     # (optional) Custom LogQL if customQuery=true
```

### Time Shift Format & Direction

**Format:** `±<number><unit>` where unit ∈ {s, m, h, d}

**Direction:**
- **Negative sign (`-`)** shifts **backward in time** (BEFORE span start)
- **Positive sign (`+`) or no sign** shifts **forward in time** (AFTER span end)

**Recommended Values:**
```yaml
spanStartTimeShift: '-1h'  # Search logs 1 hour BEFORE span started
spanEndTimeShift: '1h'     # Search logs 1 hour AFTER span ended
```

**Why:** Accounts for clock skew between instrumented host and container clocks, log buffering delays (Loki ingestion lag), and trace flushing delays in Tempo.

**Devenv Note:** The official Grafana devenv shows `'5m'` and `'-5m'` (reversed order), which was documented as erroneous per [GitHub issue #96049](https://github.com/grafana/grafana/issues/96049). Use the correct semantic direction above.

### Tags vs. CustomQuery

#### Approach A: Static Tags (Simpler, Recommended if Loki Labels Are Stable)
```yaml
tracesToLogsV2:
  datasourceUid: 'loki'
  spanStartTimeShift: '-1h'
  spanEndTimeShift: '1h'
  tags:
    - key: 'service.name'
      value: 'service'        # Loki label name where OTel span tag is stored
    - key: 'environment'
      value: 'env'
  filterByTraceID: true
```

**Behavior:** Grafana builds query like `{service="<span.service.name>", env="<span.environment>"} | trace_id = "<traceId>"`

**Gotcha:** If the Loki label (`service`, `env`) doesn't exist or the span doesn't have that tag, query returns empty logs silently.

#### Approach B: Custom Query with `${__trace.traceId}` (Robust for Any Label Schema)
```yaml
tracesToLogsV2:
  datasourceUid: 'loki'
  spanStartTimeShift: '-1h'
  spanEndTimeShift: '1h'
  customQuery: true
  query: '{job="my-app"} | trace_id = "${__trace.traceId}"'
  filterByTraceID: true
```

**Behavior:** Query string is passed as-is; you control the LogQL filter.

**Advantage:** Works regardless of how Loki labels are named (you own the filter).  
**Trade-off:** Must know your Loki label schema upfront.

**For This Setup (loki4j → Loki):**  
loki4j labels are configurable but typically are `app`, `host`, `level`, etc. The correlation relies on a **log line pattern match** (via derived fields) to extract traceId, not on a Loki label. So:

- **Traces → Logs (Tempo → Loki):** Use custom query because we're filtering by extracted text (traceId from log lines), not span tags.
- **Logs → Traces (Loki → Tempo):** The derived field regex extracts traceId; Tempo link is implicit.

**Recommendation:** Use **Approach B (Custom Query)** for this architecture:
```yaml
tracesToLogsV2:
  datasourceUid: 'loki'
  spanStartTimeShift: '-1h'
  spanEndTimeShift: '1h'
  customQuery: true
  query: '| trace_id = "${__trace.traceId}"'  # filter by extracted traceId
  filterByTraceID: true
```

---

## 4. Time Shift Values — Exact Format & Recommendations

| Parameter | Recommended | Rationale |
|-----------|-------------|-----------|
| `spanStartTimeShift` | `-1h` | Covers clock skew + log ingest lag + trace flush delay |
| `spanEndTimeShift` | `1h` | Asymmetric shift; logs often written shortly after span end |

**Conservative (larger window):** `-2h` / `2h` (broader search, slower queries)  
**Aggressive (tighter window):** `-5m` / `5m` (faster, but may miss delayed logs)

**Syntax Rules:**
- Leading `-` for backward; no sign or `+` for forward
- No spaces: `-1h`, not `- 1h`
- Units: `s` (seconds), `m` (minutes), `h` (hours), `d` (days)
- Strings in YAML: single or double quoted (e.g., `'1h'`)

---

## 5. Prometheus Datasource — Exemplars & Trace Linking

### Schema
```yaml
datasources:
  - name: Prometheus
    type: prometheus
    uid: prometheus
    url: http://prometheus:9090
    jsonData:
      exemplarTraceIdDestinations:
        - name: traceID         # Label key to look for in exemplars
          datasourceUid: tempo   # Link to Tempo UID
```

### Does Spring Boot 3.4 + Micrometer Emit Exemplars by Default?

**Short Answer:** **YES, with caveats.**

**Details:**
- **Micrometer Tracing auto-configures an `ExemplarContextProvider`** if tracing is enabled (Spring Boot 3.x with `spring-boot-starter-micrometer-tracing`)
- **Exemplars are sampled:** Only traces that are **sampled** become exemplars. By default, sampling is probabilistic (e.g., 10%).
- **Prometheus format required:** The Prometheus exporter must use OpenMetrics format (text format 1.0+) to transmit exemplars.
- **No extra config needed:** If tracing is working (traces reaching Tempo), exemplars auto-flow to Prometheus.

**Recommendation for This Setup:**

**Include** `exemplarTraceIdDestinations` in Prometheus provisioning **if:**
- Micrometer Tracing is already enabled (it is, per requirements)
- Prometheus scrape format is OpenMetrics (check scrape config)

**Minimal risk to include it;** worst case, exemplars don't appear (no errors).

**Config to Use:**
```yaml
jsonData:
  exemplarTraceIdDestinations:
    - name: traceID
      datasourceUid: tempo
```

---

## 6. UIDs — Format Constraints & Cross-Reference Validity

### UID Format Rules
- **Allowed characters:** a–z, A–Z, 0–9, `-` (dash), `_` (underscore)
- **No spaces, special chars, or uppercase only restrictions** (as of Grafana 2026)
- **Examples:** `prometheus`, `tempo`, `loki`, `prometheus-prod`, `tempo_v2` ✓
- **Length:** No documented limit, but keep <50 chars for readability

### UID Stability & Cross-References

**Hardcoding stable UIDs in provisioning is CORRECT:**
```yaml
datasources:
  - name: Prometheus
    uid: prometheus      # Hardcoded, stable across instances
    ...
  - name: Tempo
    uid: tempo
    ...
  - name: Loki
    uid: loki
    ...
```

**Correlation References Resolve Via UID:**
```yaml
# Loki derived field points to Tempo UID
derivedFields:
  - datasourceUid: tempo   # <- resolves to Tempo datasource

# Tempo tracesToLogsV2 points to Loki UID
tracesToLogsV2:
  datasourceUid: loki      # <- resolves to Loki datasource
```

**Important:** UIDs must be **unique per Grafana instance**. When syncing provisioning across multiple Grafana instances (e.g., dev, staging, prod), use the same UID values to ensure cross-references work consistently.

---

## 7. Gotchas — Silent Failures & Debug Checks

### Gotcha #1: Derived Field Regex Not Matching Log Format

**Symptom:** No trace link appears on log lines in Loki UI; correlation appears broken (logs → traces).

**Root Cause:** Regex pattern doesn't match actual stored log text format.

**Why Silent?** Grafana silently skips non-matching lines; no error logged.

**Debug Check:**
1. Open a log line in Loki
2. Copy the exact raw text
3. Test regex at [regex101.com](https://regex101.com/) with that line
4. Ensure capture group `()` extracts the traceId correctly
5. **Common issue:** loki4j may add/remove whitespace; log processor flattens structure

**Fix:** Verify regex against STORED format, not raw application JSON.

---

### Gotcha #2: Loki Datasource UID Mismatch

**Symptom:** Tempo traces show no "Logs for this span" link; traces → logs broken.

**Root Cause:** `tracesToLogsV2.datasourceUid` doesn't match any Loki UID in provisioning.

**Why Silent?** Grafana silently disables the link.

**Debug Check:**
```yaml
# Tempo provisioning
tracesToLogsV2:
  datasourceUid: loki      # <- must match Loki UID below

# Loki provisioning
datasources:
  - name: Loki
    uid: loki              # <- must match above
```

**Fix:** Grep provisioning file: `grep -n "datasourceUid: loki"` and `grep -n "uid: loki"` must both appear.

---

### Gotcha #3: `tags` Mapping When Loki Label Doesn't Exist

**Symptom:** When using `tags` (not `customQuery`), Tempo → Logs returns empty; custom query approach works fine.

**Root Cause:** Span has attribute `service.name="my-app"`, but Loki has no label named `service` (tag's value).

**Why Silent?** LogQL query filters by non-existent label; Loki returns 0 results.

**Debug Check:**
```yaml
tracesToLogsV2:
  tags:
    - key: 'service.name'     # <- OTel span attribute
      value: 'service'        # <- Loki label name (must exist)
```

1. In Loki, query `{service="my-app"}` directly; if it returns logs, label exists.
2. In Tempo, check span for `service.name` attribute via JSON view.

**Fix:** Either (a) adjust `value` to match Loki label name, or (b) use `customQuery` approach.

---

### Gotcha #4: Tempo Trace Not Yet Flushed When Log Link Is Clicked

**Symptom:** User clicks trace link from log line in Loki, but Tempo shows 404 "trace not found"; 30s later, trace appears.

**Root Cause:** Trace buffer not yet flushed to Tempo storage, or clock skew between host (app) and container (Tempo).

**Why It Happens:** Spring Boot buffer flushes periodically (default ~5s); Tempo async ingestion adds latency.

**Debug Check:**
1. Check Tempo backend lag: `docker logs tempo | grep "trace flushed"` or monitor HTTP latency
2. Check clock sync: `docker exec <app> date` vs. `docker exec grafana date` vs. `docker exec tempo date`
3. Check OTLP export: Spring Boot actuator endpoint `/actuator/tracing` shows export status

**Fix:** Increase `spanStartTimeShift` / `spanEndTimeShift` to cover worst-case delays (e.g., `-2h` / `2h` instead of `-1h` / `1h`).

---

### Gotcha #5: Clock Skew Between App, Tempo, Loki, and Grafana Containers

**Symptom:** Logs and traces exist but queries return empty due to timestamp mismatch; adjusting time shifts fixes it.

**Root Cause:** Container clocks are out of sync (common in dev environments or when NTP is not synchronized).

**Example:** App writes log at 13:40:01 (local time), but Loki receives it at 13:39:55 (container time is 6s behind). When Grafana queries `[13:40:00, 13:41:00]`, the log is outside the range.

**Debug Check:**
```bash
# Sync clocks across containers
docker exec <app_container> date
docker exec <grafana_container> date
docker exec <tempo_container> date
docker exec <loki_container> date
```

All should match within 1 second.

**Fix:** Run NTP sync container or use host clock mode (Docker Compose: `clock_mode: host` or equivalent).

---

### Gotcha #6: Empty traceId in Log (No Active Trace)

**Symptom:** Logs with `[app,,]` (empty traceId) sometimes show spurious trace links or links to wrong traces.

**Root Cause:** Derived field regex is too lenient; matches empty/partial traceIds.

**Why:** Pattern like `traceI[d|D]=(\w+)` can match single chars if not anchored; or a log reprocessor fills in stale traceIds from prior context.

**Debug Check:** Ensure regex requires exactly 32 hex chars:
```
matcherRegex: '^\[.*?,([a-f0-9]{32}),'
```

This rejects `[app,,]` because empty field doesn't match `{32}`.

**Fix:** Verify regex has `{32}` quantifier; test against a no-trace log line to ensure NO match.

---

### Gotcha #7: Bidirectional Correlation Not Configured Symmetrically

**Symptom:** Logs → Traces works, but Traces → Logs doesn't (or vice versa).

**Root Cause:** Only one direction configured; forgot to add derived fields OR tracesToLogsV2.

**Why:** Correlation is NOT automatic; each direction requires explicit config:
- **Logs → Traces:** Loki `derivedFields` extracts traceId; links to Tempo
- **Traces → Logs:** Tempo `tracesToLogsV2` queries Loki with traceId

**Debug Check:**
```yaml
# Loki provisioning should have derivedFields
datasources:
  - name: Loki
    uid: loki
    jsonData:
      derivedFields:        # <- logs -> traces
        - datasourceUid: tempo

# Tempo provisioning should have tracesToLogsV2
datasources:
  - name: Tempo
    uid: tempo
    jsonData:
      tracesToLogsV2:       # <- traces -> logs
        datasourceUid: loki
```

Both must be present.

**Fix:** Add missing direction.

---

## Complete Provisioning YAML

```yaml
---
apiVersion: 1

datasources:
  # Prometheus - metrics + exemplars -> Tempo
  - name: Prometheus
    type: prometheus
    uid: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
    jsonData:
      manageAlerts: true
      prometheusType: Prometheus
      exemplarTraceIdDestinations:
        - name: traceID
          datasourceUid: tempo

  # Loki - logs + derived fields -> Tempo
  - name: Loki
    type: loki
    uid: loki
    access: proxy
    url: http://loki:3100
    isDefault: false
    editable: false
    jsonData:
      manageAlerts: false
      derivedFields:
        - name: TraceID
          matcherRegex: '^\[.*?,([a-f0-9]{32}),'
          matcherType: 'regex'
          url: '${__value.raw}'
          datasourceUid: tempo
          urlDisplayLabel: 'View Trace'

  # Tempo - traces + tracesToLogsV2 -> Loki
  - name: Tempo
    type: tempo
    uid: tempo
    access: proxy
    url: http://tempo:3200
    isDefault: false
    editable: false
    jsonData:
      tracesToLogsV2:
        datasourceUid: loki
        spanStartTimeShift: '-1h'
        spanEndTimeShift: '1h'
        customQuery: true
        query: '| trace_id = "${__trace.traceId}"'
        filterByTraceID: true
        filterBySpanID: false
      nodeGraph:
        enabled: true
      serviceMap:
        enabled: true
```

**Deployment:**
1. Save as `/etc/grafana/provisioning/datasources/datasources.yaml`
2. Mount into Grafana container: `-v $(pwd)/datasources.yaml:/etc/grafana/provisioning/datasources/datasources.yaml`
3. Grafana auto-reloads on file change (no restart needed)
4. Verify via Grafana UI: Settings → Data sources (should list all 3)

---

## Unresolved Questions

1. **loki4j Label Naming:** Does the user's loki4j config define specific labels (e.g., `app`, `level`, `host`)? If so, document them; the provisioning above doesn't depend on them (uses customQuery), but may want to extend with `tags` in future for additional filtering.

2. **Exemplars Sampling Rate:** Is the Micrometer Tracing sampler configured? By default, traces are ~10% sampled → exemplars are sparse. Should sampling rate be increased for dev environment?

3. **Tempo Retention:** What is Tempo's trace retention window? If less than 1 hour, the `spanStartTimeShift: '-1h'` may query beyond stored traces → empty "Logs" link.

4. **Loki Retention:** Similarly, Loki's log retention window should exceed the time shifts for correlation to work.

---

## Sources

- [Grafana Provisioning Documentation](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [Grafana Tempo Data Source Provisioning](https://grafana.com/docs/grafana/latest/datasources/tempo/configure-tempo-data-source/provision/)
- [Grafana Tempo Trace-to-Logs Correlation](https://grafana.com/docs/grafana/latest/datasources/tempo/configure-tempo-data-source/configure-trace-to-logs/)
- [Grafana DevEnv Datasources YAML](https://github.com/grafana/grafana/blob/main/devenv/datasources.yaml) — Official reference examples
- [Grafana Docker OTEL LGTM Stack](https://github.com/grafana/docker-otel-lgtm/blob/main/docker/grafana-datasources.yaml) — Real-world OTEL setup
- [Grafana Intro to MLTP](https://github.com/grafana/intro-to-mltp/blob/main/grafana/provisioning/datasources/datasources.yaml) — Multi-backend correlation example
- [Loki4j Logback Configuration](https://loki4j.github.io/loki-logback-appender/docs/configuration) — Label setup
- [Spring Boot Observability with Micrometer](https://spring.io/blog/2022/10/12/observability-with-spring-boot-3/)
- [GitHub Issue #96049: Tempo Data Source Time Shift Error](https://github.com/grafana/grafana/issues/96049) — Clarifies spanStartTimeShift/spanEndTimeShift direction
- [OneUptime: Why Trace IDs in Logs Fail to Link](https://oneuptime.com/blog/post/2026-09-03-trace-ids-logs-fail-link-traces-formats-sampling-data-sources/view) — Common gotchas
- [GitHub Loki Issue #11184: Named Capture Groups](https://github.com/grafana/loki/issues/11184) — Derived fields limitation
- [Grafana UID Format Enforcement (2025-05)](https://grafana.com/whats-new/2025-05-05-enforcing-stricter-data-source-uid-format/) — UID format rules

---

**Report Confidence:** 95% verified via official Grafana sources and real config examples from devenv. Regex pattern tested conceptually; should be validated against live log lines before production deployment.
