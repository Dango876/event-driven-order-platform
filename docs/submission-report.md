# Submission Report (MVP)

## 1) Project scope

This repository implements an event-driven microservice platform for small e-commerce order processing:

- api-gateway
- auth-service
- user-service
- product-service
- inventory-service
- order-service
- notification-service

Main stack used:

- Java 17, Spring Boot 3, Spring Security 6
- Kafka-compatible broker (Redpanda) + Schema Registry
- PostgreSQL, MongoDB, Redis
- Docker + k3d + Helm

## 2) What was completed

- Local one-command startup for Docker Compose:
  - `./dev-up.ps1`
  - `./dev-down.ps1`
- Local Kubernetes startup for k3d + Helm:
  - `./infra/k8s/k3d-up.ps1`
  - `./infra/k8s/k3d-down.ps1`
- Kubernetes startup hardening for unstable infra startup:
  - k3d node `fs.aio-max-nr` tuning in `k3d-up.ps1`
  - safer namespace check for missing namespace
  - Helm deploy retry with diagnostics + infra recovery for `redpanda` and `schema-registry`
  - deployment settings in Helm values/templates to stabilize Redpanda and Schema Registry startup
- Local observability baseline:
  - Prometheus + Grafana + Loki + Promtail in Docker Compose
  - preloaded Grafana dashboard: `EDOP Local / EDOP Local Observability`
  - metrics/logs verification documented in `docs/observability-local-run.md`
- Local alerting baseline:
  - Alertmanager service in Docker Compose
  - Prometheus alert rules:
    - `EdopServiceDown`
    - `EdopHigh5xxRate`
    - `EdopHighP95Latency`

## 3) Repro steps (k3d + Helm)

From repository root:

```powershell
.\infra\k8s\k3d-down.ps1
.\infra\k8s\k3d-up.ps1
```

Expected result:

- Helm release `edop` is deployed in namespace `edop-dev`
- all application services are running
- infra recovery logic handles first-attempt startup issues if needed

Verification commands:

```powershell
kubectl get pods -n edop-dev
.\infra\k8s\smoke-check.ps1
.\infra\k8s\order-lifecycle-check.ps1 -RequestTimeoutSec 90 -OrderCreateRetries 5 -RetryDelaySec 5
```

Expected checks:

- smoke check returns HTTP 200 for health, Swagger UI, and API docs endpoints
- order lifecycle check passes all transitions:
  - `RESERVED -> PAID -> SHIPPED -> COMPLETED`
  - invalid transition is rejected with `409`

## 4) Repro steps (docker-compose)

From repository root:

```powershell
.\dev-up.ps1
.\infra\k8s\smoke-check.ps1
Invoke-WebRequest http://localhost:9090/-/ready -UseBasicParsing
Invoke-WebRequest http://localhost:9093/-/ready -UseBasicParsing
Invoke-WebRequest http://localhost:3100/ready -UseBasicParsing
.\dev-down.ps1
```

## 5) API and docs

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`
- Grafana: `http://localhost:3000`
- Loki readiness: `http://localhost:3100/ready`
- API docs via gateway paths:
  - `/api-docs/auth`
  - `/api-docs/user`
  - `/api-docs/product`
  - `/api-docs/inventory`
  - `/api-docs/order`
  - `/api-docs/notification`

Detailed docs:

- `docs/acceptance-checklist.md`
- `docs/k3d-helm-local-run.md`
- `docs/observability-local-run.md`
- `docs/order-service-local-verification.md`
- `docs/swagger-openapi-endpoints.md`

## 6) Current status vs acceptance points

- Local one-command startup: done
- k3d/k8s local deployment: done
- Swagger/OpenAPI availability via gateway: done
- End-to-end order lifecycle: done
- Reproducible verification scripts: done
- Observability baseline (metrics + logs + dashboard): done
- Alerting baseline (Prometheus rules + Alertmanager): done

## 7) Notes

- This is an educational MVP focused on local reproducibility and service integration.
- Security scans are integrated in CI and currently passing.
- Remaining next-iteration items are production-grade load/SLO validation and external alert routing integrations.

## 8) CI/Security evidence

- CI (green): https://github.com/Dango876/event-driven-order-platform/actions/runs/23296018313
- Security Scan (green, latest workflow view): https://github.com/Dango876/event-driven-order-platform/actions/workflows/security.yml
