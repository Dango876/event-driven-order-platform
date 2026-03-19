# Acceptance Checklist (Week 1/2 + Observability Baseline)

## Scope

This checklist closes Week 1 and Week 2 outcomes from the project plan:
- Week 1: project skeleton, CI baseline, Docker/Helm basics, runnable services.
- Week 2: auth + gateway + product/inventory CRUD baseline + Kafka/Schema Registry integration baseline.
- Observability baseline: Prometheus + Grafana + Loki + Alertmanager with local verification.

Date of latest verification: 2026-03-19 (Europe/Moscow).

## 0. One-command local startup (required)

Commands:
- PowerShell: `./dev-up.ps1`
- Stop: `./dev-down.ps1`

Expected:
- Infra containers are up.
- All microservices are reachable.
- Gateway health is `UP`.

Verification status:
- `PASS` on Windows PowerShell.
- Reproducible sequence:
  1. `./dev-down.ps1`
  2. `./dev-up.ps1`
  3. `Invoke-RestMethod http://localhost:8080/actuator/health`
  4. `./infra/k8s/smoke-check.ps1`
- Smoke check passed for:
  - `/actuator/health`
  - `/swagger-ui.html`
  - `/api-docs/auth`
  - `/api-docs/user`
  - `/api-docs/product`
  - `/api-docs/inventory`
  - `/api-docs/order`
  - `/api-docs/notification`

## 1. Build and tests

Command:
- `mvn -U test`

Expected:
- Reactor build success.
- Tests executed where present.

Verification status:
- `PASS` (latest successful run under JDK 17).
- Latest run finished at: `2026-03-18T10:03:21+03:00`.
- Reactor: `9/9 SUCCESS`.
- Test summary:
  - `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`
  - `com.procurehub.order.api.OrderLifecycleIntegrationTest` passed.
- Note:
  - Several modules still contain no test classes (`No tests to run`), so coverage expansion remains backlog work.

## 2. Service health

Health endpoints:
- `http://localhost:8080/actuator/health` (api-gateway)
- `http://localhost:8081/actuator/health` (auth-service)
- `http://localhost:8082/actuator/health` (user-service)
- `http://localhost:8083/actuator/health` (product-service)
- `http://localhost:8084/actuator/health` (inventory-service)
- `http://localhost:8086/actuator/health` (order-service)
- `http://localhost:8087/actuator/health` (notification-service)

Expected:
- All return `UP`.

Verification status:
- `PASS` in local dev-up flow.

## 3. Swagger/OpenAPI

Gateway endpoints:
- UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

Expected:
- Swagger UI opens.
- Aggregated API docs are reachable via gateway routes.

Verification status:
- `PASS` via smoke-check (`200` for all listed gateway API-docs endpoints).

## 4. Order lifecycle smoke

Command:
- `./infra/k8s/order-lifecycle-check.ps1 -RequestTimeoutSec 90 -OrderCreateRetries 5 -RetryDelaySec 5`

Expected:
- Lifecycle transitions: `RESERVED -> PAID -> SHIPPED -> COMPLETED`
- Invalid transition `COMPLETED -> CANCELLED` rejected with `409`.

Verification status:
- `PASS`.
- Observed output:
  - Order created
  - Transitioned to `PAID`
  - Transitioned to `SHIPPED`
  - Transitioned to `COMPLETED`
  - Invalid transition rejected with `409`

## 5. Week 1/2 closure summary

Status: `CLOSED` for local acceptance baseline.

Covered by evidence above:
- Local stack starts with one command.
- Gateway + service docs/health reachable.
- Build/test command succeeds.
- End-to-end order lifecycle baseline passes.
- Observability and alerting baseline is available and verified locally.

## 6. Kubernetes acceptance artifact (k3d + Helm)

Verification status:
- `PASS` on local k3d + Helm run.

Evidence:
- `.\infra\k8s\k3d-down.ps1`
- `.\infra\k8s\k3d-up.ps1`
- `.\infra\k8s\smoke-check.ps1` passed
- `.\infra\k8s\order-lifecycle-check.ps1` passed

## 7. CI and security evidence (green)

Verification status:
- `PASS` for CI workflow and security workflow.
- CI (green): `https://github.com/Dango876/event-driven-order-platform/actions/runs/23296018313`
- Security Scan (green, latest workflow view): `https://github.com/Dango876/event-driven-order-platform/actions/workflows/security.yml`

Notes:
- OWASP step is configured with NVD API key usage and retry/delay hardening for CI stability.
- Trivy gate passed after Avro update to fixed version (`1.11.4`).

## 8. Observability and alerting baseline (local)

Verification status:
- `PASS` on local Docker Compose stack (`.\dev-up.ps1`).

Covered:
- Metrics:
  - Prometheus endpoint: `http://localhost:9090`
  - Service `/actuator/prometheus` scraping for gateway and all services.
- Logs:
  - Loki readiness: `http://localhost:3100/ready`
  - Promtail collection from `.logs/*.out.log` and `.logs/*.err.log`
  - Grafana Explore query verified: `{job="edop-local"}`
- Dashboard:
  - Grafana: `http://localhost:3000`
  - Preloaded dashboard: `Dashboards -> EDOP Local -> EDOP Local Observability`
  - Panels verified: `HTTP RPS`, `HTTP 5xx Rate`, `HTTP p95 Latency (ms)`, `Recent ERROR Logs`.
- Alerting:
  - Alertmanager readiness: `http://localhost:9093/-/ready`
  - External webhook routing configured via `ALERT_WEBHOOK_URL`
  - Local webhook sink endpoint: `http://localhost:8088`
  - Alert rules present in Prometheus:
    - `EdopServiceDown`
    - `EdopHigh5xxRate`
    - `EdopHighP95Latency`

Evidence:
- Local verification commands:
  - `(Invoke-WebRequest http://localhost:9090/-/ready -UseBasicParsing).StatusCode`
  - `(Invoke-WebRequest http://localhost:9093/-/ready -UseBasicParsing).StatusCode`
  - `(Invoke-WebRequest http://localhost:8088 -UseBasicParsing).StatusCode`
  - `(Invoke-WebRequest http://localhost:3000/api/health -UseBasicParsing).StatusCode`
  - `(Invoke-WebRequest http://localhost:3100/ready -UseBasicParsing).Content`
  - `Invoke-WebRequest http://localhost:9090/api/v1/rules -UseBasicParsing`

## 9. Load/SLO baseline (local)

Verification status:
- `PASS` on local k6 baseline run.

Evidence:
- command: `.\infra\performance\run-load-baseline.ps1 -Duration 20s -Vus 20`
- observed result:
  - `http_req_failed`: `0%` (`PASS`, threshold `< 1%`)
  - `http_req_duration p95`: `7.23ms` (`PASS`, threshold `< 750ms`)
  - `checks pass rate`: `100%` (`PASS`, threshold `> 99%`)
  - request rate baseline: `~128.82 req/s`

## 10. Remaining acceptance items (outside current baseline)

Still to be finalized against full production-grade acceptance:
- Extended multi-scenario load/performance validation (beyond local baseline), with documented long-run SLA/SLO envelopes.
- Production alert routing governance (on-call schedule/escalation policy and receiver hardening).
