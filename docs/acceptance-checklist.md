# Acceptance Checklist (Week 1/2)

## Scope

This checklist closes Week 1 and Week 2 outcomes from the project plan:
- Week 1: project skeleton, CI baseline, Docker/Helm basics, runnable services.
- Week 2: auth + gateway + product/inventory CRUD baseline + Kafka/Schema Registry integration baseline.

Date of latest verification: 2026-03-18 (Europe/Moscow).

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

## 6. Remaining acceptance items (outside Week 1/2 closure)

Still to be finalized later against full project acceptance:
- Security scanning target (`OWASP Dependency-Check + Trivy`, 0 critical).
- CI quality gates (including coverage target) in final pipeline form.
- Stable repeatable Kubernetes acceptance run (k3d + Helm) as final check artifact.
