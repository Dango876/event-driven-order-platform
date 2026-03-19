# Local Observability Run (Prometheus + Alertmanager + Grafana + Loki)

## Goal

Run local observability for all services and verify:
- metrics are scraped by Prometheus;
- logs are collected by Promtail and available in Loki/Grafana.

## Prerequisites

- Docker Desktop running
- Local stack started with:

```powershell
.\dev-up.ps1
```

## Endpoints

- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`
- Loki: `http://localhost:3100/ready`
- Grafana: `http://localhost:3000`
  - login: `admin`
  - password: `admin`
  - preloaded dashboard: `Dashboards -> EDOP Local -> EDOP Local Observability`

## What is scraped

Prometheus scrapes `/actuator/prometheus` from:

- api-gateway (`localhost:8080`)
- auth-service (`localhost:8081`)
- user-service (`localhost:8082`)
- product-service (`localhost:8083`)
- inventory-service (`localhost:8084`)
- order-service (`localhost:8086`)
- notification-service (`localhost:8087`)

And Redpanda admin metrics from:

- `redpanda:9644/metrics`

Logs are collected from local files:

- `.logs/*.out.log`
- `.logs/*.err.log`

Prometheus alert rules are loaded from:

- `infra/observability/prometheus/rules/edop-alerts.yml`

Configured baseline alerts:

- `EdopServiceDown` (critical): service target is down for > 1m
- `EdopHigh5xxRate` (warning): 5xx ratio > 5% for > 5m
- `EdopHighP95Latency` (warning): p95 latency > 1.5s for > 5m

## Quick verification

1. Open Prometheus and check target health:
   - `Status -> Targets`
   - Expected: service targets are `UP`.
2. Run a query in Prometheus:
   - `http_server_requests_seconds_count`
3. Check alert rules in Prometheus:
   - `Alerts -> Alerting rules`
   - Expected: `EdopServiceDown`, `EdopHigh5xxRate`, `EdopHighP95Latency`.
4. Open Alertmanager:
   - `http://localhost:9093`
   - Expected: UI is available.
5. Open Loki readiness endpoint:
   - `http://localhost:3100/ready`
   - Expected: `ready`.
6. Open Grafana:
   - `Connections -> Data sources`
   - Expected: `Prometheus` and `Loki` exist and are healthy.
7. In Grafana open `Explore`:
   - choose datasource `Loki`
   - run query: `{job="edop-local"}`
   - Expected: logs from local services are visible.
8. Open dashboard:
   - `Dashboards -> EDOP Local -> EDOP Local Observability`
   - Expected panels:
     - `HTTP RPS`
     - `HTTP 5xx Rate`
     - `HTTP p95 Latency (ms)`
     - `Recent ERROR Logs`

## Alert smoke check (optional)

You can force `EdopServiceDown` and observe it in Prometheus/Alertmanager.

1. Stop one service process (example: order-service on port `8086`):

```powershell
Get-NetTCPConnection -LocalPort 8086 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

2. Wait ~1-2 minutes (rule has `for: 1m`), then check:
   - Prometheus alerts API: `http://localhost:9090/api/v1/alerts`
   - Alertmanager UI: `http://localhost:9093`

3. Start stack again:

```powershell
.\dev-up.ps1
```

## Stop

```powershell
.\dev-down.ps1
```
