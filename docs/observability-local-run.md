# Local Observability Run (Prometheus + Grafana)

## Goal

Run local monitoring for all services and verify that metrics are scraped.

## Prerequisites

- Docker Desktop running
- Local stack started with:

```powershell
.\dev-up.ps1
```

## Endpoints

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
  - login: `admin`
  - password: `admin`

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

## Quick verification

1. Open Prometheus and check target health:
   - `Status -> Targets`
   - Expected: service targets are `UP`.
2. Run a query in Prometheus:
   - `http_server_requests_seconds_count`
3. Open Grafana:
   - `Connections -> Data sources`
   - Expected: `Prometheus` exists and is healthy.

## Stop

```powershell
.\dev-down.ps1
```
