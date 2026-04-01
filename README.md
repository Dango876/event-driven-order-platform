# Event-Driven Order Platform

Local development and validation commands for the project.

## Current verified status

Latest end-to-end verification completed on `2026-04-01` (Europe/Moscow).

Confirmed:
- local full test suite is green: `mvn -B -ntp test`
- local aggregated JaCoCo service coverage is green: `80.1211%`
- GitHub Actions are green for commit `da59949`:
  - `CI`
  - `Security Scan`
  - `CD`
- runtime checks passed on local Docker Compose stack:
  - `inventory-service` consumes `order.created` and publishes `inventory.reserved`
  - `api-gateway` -> `product-service` gRPC read path returns the same product payload as HTTP
  - `ROLE_USER` sees only own orders; another user gets `404` on direct access to another user's order
  - order lifecycle check passes:
    - `RESERVATION_PENDING -> RESERVED -> PAID -> SHIPPED -> COMPLETED`
    - invalid transition `COMPLETED -> CANCELLED` is rejected with `409`

## Local one-command startup

PowerShell:

```powershell
.\dev-up.ps1
```

Note:
- Do not run `k3d` stack and local `dev-up` stack on the same host port `8080` at the same time.
- If port `8080` is occupied by `wslrelay`/k3d, stop k3d first: `.\infra\k8s\k3d-down.ps1`.

GNU Make:

```powershell
make dev-up
```

Expected result:
- Full local stack is started with Docker Compose:
  - infra containers: Redpanda, Schema Registry, Postgres, MongoDB, Redis, Prometheus, Alertmanager, Grafana, Loki, Promtail
  - application containers: api-gateway, auth-service, user-service, product-service, inventory-service, order-service, notification-service
- Services are healthy.
- Gateway is available at `http://localhost:8080`.
- Swagger UI is available at `http://localhost:8080/swagger-ui.html`.
- Prometheus is available at `http://localhost:9090`.
- Alertmanager is available at `http://localhost:9093`.
- Local alert webhook sink is available at `http://localhost:8088`.
- Jaeger UI is available at `http://localhost:16686`.
- Loki is available at `http://localhost:3100/ready`.
- Grafana is available at `http://localhost:3000` (`admin`/`admin` by default).
- Preloaded Grafana dashboard: `EDOP Local / EDOP Local Observability`.
- Application logs are written to `.logs/*.app.log` and collected by Promtail/Loki.

## Stop local stack

PowerShell:

```powershell
.\dev-down.ps1
```

GNU Make:

```powershell
make dev-down
```

Expected result:
- Docker Compose stops the full local stack.
- If any supported dev process is still listening on service ports (`8080-8087`, `9091`), `.\dev-down.ps1` also frees those ports.

## Smoke check

```powershell
.\infra\k8s\smoke-check.ps1
```

## Order lifecycle check

```powershell
.\infra\k8s\order-lifecycle-check.ps1
```

## Load/SLO baseline

```powershell
.\infra\performance\run-load-baseline.ps1
```

## SLA validation profile (500 RPS target)

```powershell
.\infra\performance\run-sla-validation.ps1
```

## SLA evidence profile (k8s-like, archived artifacts)

```powershell
.\infra\performance\run-sla-k8s-evidence.ps1
```

## Additional docs

- `docs/acceptance-checklist.md`
- `docs/alert-routing.md`
- `docs/ci-cd-pipeline.md`
- `docs/k3d-helm-local-run.md`
- `docs/k8s-scaling-availability-baseline.md`
- `docs/load-slo-baseline.md`
- `docs/performance-sla-validation.md`
- `docs/performance-sla-k8s-evidence.md`
- `docs/redis-distributed-locks.md`
- `docs/notification-rate-limit.md`
- `docs/observability-local-run.md`
- `docs/order-service-local-verification.md`
- `docs/project-passport.md`
- `docs/submission-report.md`
- `docs/swagger-openapi-endpoints.md`
- `docs/tls-secrets-k8s-baseline.md`

## RBAC foundation

JWT-based RBAC is enabled for business services and validated through the gateway at `http://localhost:8080`.

Current access model:

- `auth-service`
  - authenticated users can call `/api/auth/me`
  - only `ROLE_ADMIN` can call `/api/auth/admin/**`
- `product-service`
  - `GET /api/products` and `GET /api/products/{id}` are available for `ROLE_USER`, `ROLE_MANAGER`, `ROLE_ADMIN`
  - product write operations are restricted to `ROLE_ADMIN`
- `user-service`
  - all `/api/users` endpoints are restricted to `ROLE_ADMIN`
- `order-service`
  - `POST /api/orders`, `GET /api/orders`, `GET /api/orders/{id}` are available for `ROLE_USER` and `ROLE_ADMIN`
  - reserve/release/stock/status-management endpoints are restricted to `ROLE_ADMIN`

Gateway routing covers both collection and nested API paths:

- `/api/auth` and `/api/auth/**`
- `/api/users` and `/api/users/**`
- `/api/products` and `/api/products/**`
- `/api/inventory` and `/api/inventory/**`
- `/api/orders` and `/api/orders/**`
- `/api/notifications` and `/api/notifications/**`

## RBAC smoke check

Example verification flow through the gateway:

```powershell
$userLoginBody = @{ email = "rbac-user-2@example.com"; password = "Password123!" } | ConvertTo-Json -Compress
$adminLoginBody = @{ email = "rbac-admin-2@example.com"; password = "Password123!" } | ConvertTo-Json -Compress

$userLogin = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body $userLoginBody
$adminLogin = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body $adminLoginBody

$USER_TOKEN = $userLogin.accessToken
$ADMIN_TOKEN = $adminLogin.accessToken

curl.exe -s -o NUL -w "%{http_code}`n" -H "Authorization: Bearer $USER_TOKEN" http://localhost:8080/api/auth/me
curl.exe -s -o NUL -w "%{http_code}`n" -H "Authorization: Bearer $USER_TOKEN" http://localhost:8080/api/users
curl.exe -s -o NUL -w "%{http_code}`n" -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/users
curl.exe -s -o NUL -w "%{http_code}`n" -H "Authorization: Bearer $USER_TOKEN" http://localhost:8080/api/orders
curl.exe -s -o NUL -w "%{http_code}`n" -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/orders/stock/1001
```

Expected result:

- `/api/auth/me` with `USER` -> `200`
- `/api/users` with `USER` -> `403`
- `/api/users` with `ADMIN` -> `200`
- `/api/orders` with `USER` -> `200`
- `/api/orders/stock/1001` with `ADMIN` -> `200`

## Latest runtime evidence

Verification repeated on `2026-04-01` through `.\dev-up.ps1` local stack:

- gateway health: `UP`
- Prometheus: `200`
- Alertmanager: `200`
- Jaeger: `200`
- Grafana: `200`
- Loki: `ready`
- alert webhook sink: reachable on `http://localhost:8088`
- `inventory-service` logs confirmed:
  - `Inventory-service consumed order.created: orderId=11, productId=2001, quantity=2`
  - `Published inventory.reserved for orderId=11, productId=2001`
- `order-service` logs confirmed:
  - order `11` created with `RESERVATION_PENDING`
  - reservation event consumed and order moved to `RESERVED`
- `notification-service` logs confirmed notification flow for verified order events
