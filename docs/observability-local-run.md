# Local Observability Run (Prometheus + Grafana + Loki)

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

## Quick verification

1. Open Prometheus and check target health:
   - `Status -> Targets`
   - Expected: service targets are `UP`.
2. Run a query in Prometheus:
   - `http_server_requests_seconds_count`
3. Open Loki readiness endpoint:
   - `http://localhost:3100/ready`
   - Expected: `ready`.
4. Open Grafana:
   - `Connections -> Data sources`
   - Expected: `Prometheus` and `Loki` exist and are healthy.
5. In Grafana open `Explore`:
   - choose datasource `Loki`
   - run query: `{job="edop-local"}`
   - Expected: logs from local services are visible.
6. Open dashboard:
   - `Dashboards -> EDOP Local -> EDOP Local Observability`
   - Expected panels:
     - `HTTP RPS`
     - `HTTP 5xx Rate`
     - `HTTP p95 Latency (ms)`
     - `Recent ERROR Logs`

## Stop

```powershell
.\dev-down.ps1
```
