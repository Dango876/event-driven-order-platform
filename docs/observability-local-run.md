# Local Observability Run (Prometheus + Alertmanager + Grafana + Loki + Jaeger)

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
- Alert webhook sink: `http://localhost:8088`
- Jaeger UI: `http://localhost:16686`
- Loki: `http://localhost:3100/ready`
- Grafana: `http://localhost:3000`
  - login: `admin`
  - password: `admin`
  - preloaded dashboard: `Dashboards -> EDOP Local -> EDOP Local Observability`

## What is scraped

Prometheus scrapes `/actuator/prometheus` from internal Docker Compose targets:

- `api-gateway:8080`
- `auth-service:8081`
- `user-service:8082`
- `product-service:8083`
- `inventory-service:8084`
- `order-service:8086`
- `notification-service:8087`

And Redpanda admin metrics from:

- `redpanda:9644/metrics`

Logs are collected from local files written by containerized services:

- `.logs/*.app.log`

Each service writes its Spring Boot application log to a dedicated file:

- `api-gateway.app.log`
- `auth-service.app.log`
- `user-service.app.log`
- `product-service.app.log`
- `inventory-service.app.log`
- `order-service.app.log`
- `notification-service.app.log`

Prometheus alert rules are loaded from:

- `infra/observability/prometheus/rules/edop-alerts.yml`

Alertmanager external routing is configured via webhook URL:

- env var: `ALERT_WEBHOOK_URL`
- default in local stack: `http://alert-webhook-sink:8080/alerts`
- override example (real external receiver):
  - PowerShell session: `$env:ALERT_WEBHOOK_URL = "https://your-webhook.example/alerts"`
  - then restart stack: `.\dev-down.ps1` and `.\dev-up.ps1`

Tracing export is configured via OTLP:

- env var: `OTEL_EXPORTER_OTLP_ENDPOINT`
- default inside application containers: `http://jaeger:4318/v1/traces`
- host-exposed Jaeger OTLP HTTP endpoint: `http://localhost:4318/v1/traces`

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
5. Open local webhook sink:
   - `http://localhost:8088`
   - Expected: requests appear when an alert fires.
6. Open Loki readiness endpoint:
   - `http://localhost:3100/ready`
   - Expected: `ready`.
7. Open Jaeger:
   - `http://localhost:16686`
   - Expected: traces are visible after API traffic.
8. Open Grafana:
   - `Connections -> Data sources`
   - Expected: `Prometheus` and `Loki` exist and are healthy.
9. Confirm fresh application log files exist:
   - PowerShell: `Get-ChildItem .\.logs\*.app.log`
   - Expected: files have current timestamps after `.\dev-up.ps1`.
10. In Grafana open `Explore`:
   - choose datasource `Loki`
   - run query: `{job="edop-local"}`
   - Expected: logs from local services are visible.
11. Open dashboard:
   - `Dashboards -> EDOP Local -> EDOP Local Observability`
   - Expected panels:
     - `HTTP RPS`
     - `HTTP 5xx Rate`
     - `HTTP p95 Latency (ms)`
     - `Recent ERROR Logs`

## Alert smoke check (optional)

You can force `EdopServiceDown` and observe it in Prometheus/Alertmanager.

1. Stop one service container (example: `order-service`):

```powershell
docker compose stop order-service
```

2. Wait ~1-2 minutes (rule has `for: 1m`), then check:
   - Prometheus alerts API: `http://localhost:9090/api/v1/alerts`
   - Alertmanager UI: `http://localhost:9093`

3. Start the stopped container again:

```powershell
docker compose start order-service
```

## Stop

```powershell
.\dev-down.ps1
```
