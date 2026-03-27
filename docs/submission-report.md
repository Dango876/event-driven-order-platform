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

- Local one-command startup for full Docker Compose stack:
  - `./dev-up.ps1`
  - `./dev-down.ps1`
  - `dev-up` now starts infra and all application services as containers
  - `dev-down` stops Compose stack and frees supported host-run service ports (`8080-8087`, `9091`)
- User management API baseline in `user-service`:
  - list/get by id
  - create/update/delete
  - filters by `email` and `role`
- Local Kubernetes startup for k3d + Helm:
  - `./infra/k8s/k3d-up.ps1`
  - `./infra/k8s/k3d-down.ps1`
- Kubernetes startup hardening for unstable infra startup:
  - k3d node `fs.aio-max-nr` tuning in `k3d-up.ps1`
  - safer namespace check for missing namespace
  - Helm deploy retry with diagnostics + infra recovery for `redpanda` and `schema-registry`
  - deployment settings in Helm values/templates to stabilize Redpanda and Schema Registry startup
- Local observability baseline:
  - Prometheus + Grafana + Loki + Promtail + Jaeger in Docker Compose
  - containerized Spring Boot services write logs to `.logs/*.app.log`
  - preloaded Grafana dashboard: `EDOP Local / EDOP Local Observability`
  - metrics/logs verification documented in `docs/observability-local-run.md`
  - tracing export via OTLP to Jaeger (`http://localhost:4318/v1/traces`)
- Local alerting baseline:
  - Alertmanager service in Docker Compose
  - external webhook routing via `ALERT_WEBHOOK_URL` (default local sink on `http://localhost:8088`)
  - Prometheus alert rules:
    - `EdopServiceDown`
    - `EdopHigh5xxRate`
    - `EdopHighP95Latency`
- Alert governance baseline:
  - severity-based Alertmanager routing:
    - `critical` -> `oncall-critical` (`ALERT_ONCALL_WEBHOOK_URL`)
    - `warning` -> `external-webhook` (`ALERT_WEBHOOK_URL`)
- Notification rate limiting baseline (Redis leaky bucket):
  - `notification-service` uses Redis-backed leaky bucket for per-user notification throttling
  - `orderId -> userId` mapping is cached in Redis to apply user-level limits for status-change events
- OAuth2 login hardening baseline:
  - social OAuth2 login flow covered with unit tests in `auth-service`
  - new OAuth-created users now publish `user.created` event for cross-service consistency
- RBAC foundation across gateway and business services:
  - `auth-service` issues HS256 JWTs with roles in claim `roles`
  - `product-service`, `user-service`, and `order-service` validate the same JWT secret as resource servers
  - `product-service` write operations are restricted to `ROLE_ADMIN`
  - `user-service` `/users` endpoints are restricted to `ROLE_ADMIN`
  - `order-service` user order flow is available for `ROLE_USER`/`ROLE_ADMIN`, while reserve/release/stock/status-management endpoints are restricted to `ROLE_ADMIN`
  - gateway route predicates were widened to cover both collection and nested paths, for example `/api/users` and `/api/users/**`
  - RBAC verification through gateway completed on `2026-03-25` with expected `200/403` behavior for `auth-service`, `user-service`, and `order-service`
- Order saga runtime verification and cleanup completed on `2026-03-26`:
  - Flyway migration `V3__allow_saga_order_statuses.sql` was applied successfully to extend the order status check constraint for saga statuses
  - happy-path verification through gateway confirmed:
    - `RESERVATION_PENDING -> RESERVED -> PAID -> SHIPPED -> COMPLETED`
    - invalid transition `COMPLETED -> CANCELLED` is rejected with HTTP `409`
  - fail-path verification through gateway confirmed:
    - order creation returns `RESERVATION_PENDING`
    - Kafka-driven inventory rejection moves the order to `RESERVATION_FAILED`
  - Kafka and Schema Registry evidence confirmed the expected event flow:
    - topics: `inventory.reserve-failed`, `inventory.reserved`, `order.created`, `order.status-changed`
    - subjects: `inventory.reserve-failed-value`, `inventory.reserved-value`, `order.created-value`, `order.status-changed-value`
  - `order-service` fail-path handling was cleaned up to use a single event-driven path; warning `Received inventory.reserve-failed for missing orderId=...` is no longer reproduced in runtime logs
  - local `order-service` test run on Windows completed with `43` tests, `0` failures, `0` errors, `1` skipped (`OrderLifecycleWebTestClientIT`, skipped only because of local Testcontainers/Docker Desktop detection)
- Notification-service runtime verification and persistence completed on `2026-03-26`:
  - `notification-service` now consumes Kafka topics:
    - `user.created`
    - `order.created`
    - `order.status-changed`
  - `notification-service` now uses MongoDB for persistent notification data:
    - collection `notification_users` stores local recipient projection by user
    - collection `notification_records` stores notification delivery history
  - real email delivery is implemented via Spring Mail and verified locally through Mailpit
  - runtime verification confirmed:
    - user projection was populated from `user.created`
    - order `#6` produced email `Order #6 created`
    - order status change `RESERVATION_PENDING -> RESERVED` produced email `Order #6 status changed to RESERVED`
  - MongoDB evidence confirmed two persisted notification records with status `SENT` for order `#6`
  - Mailpit inbox confirmed successful delivery to `rbac-user-2@example.com`
- Kubernetes local runtime verification in `k3d` + Helm completed on `2026-03-27`:
  - Helm release `edop` in namespace `edop-dev` deployed successfully
  - Helm values now include `mailpit` and Kubernetes runtime configuration for `notification-service` (`MONGODB_URI`, `MAIL_HOST`, `MAIL_PORT`, `NOTIFICATION_FROM`)
  - `api-gateway` and `notification-service` disable Kubernetes service-link environment injection to avoid invalid generated `*_PORT` values
  - `notification-service` liveness/readiness probes now use lightweight `/health`, which fixed `CrashLoopBackOff` seen with `/actuator/health` in local `k3d`
  - gateway smoke checks passed for:
    - `/actuator/health`
    - `/swagger-ui.html`
    - `/api-docs/auth`
    - `/api-docs/user`
    - `/api-docs/product`
    - `/api-docs/inventory`
    - `/api-docs/order`
    - `/api-docs/notification`
- Load/SLO baseline tooling:
  - k6 scenario: `infra/performance/k6-gateway-baseline.js`
  - run helper: `infra/performance/run-load-baseline.ps1`
  - baseline guide: `docs/load-slo-baseline.md`
- SLA validation profile tooling:
  - k6 constant-arrival-rate profile: `infra/performance/k6-gateway-sla.js`
  - run helper: `infra/performance/run-sla-validation.ps1`
  - guide: `docs/performance-sla-validation.md`
- Archived long-run SLA evidence on k3d:
  - verification date: `2026-03-22`
  - profile: `500 RPS / 10m`
  - result:
    - request rate: `500.06 req/s`
    - p95 latency: `3.21ms`
    - failed rate: `0%`
    - checks pass rate: `100%`
  - archived files:
    - `infra/performance/evidence/k6-sla-summary-20260322-145202.json`
    - `infra/performance/evidence/k6-sla-evidence-20260322-145202.md`
- TLS + secrets k8s baseline:
  - optional ingress TLS for `api-gateway` in Helm chart
  - optional TLS secret upsert in CD from GitHub Secrets
  - optional `envFromSecrets` wiring in deployment template
- Kubernetes scaling/availability baseline:
  - default rolling strategy for applications (`maxUnavailable: 0`, `maxSurge: 1`)
  - HPA template baseline (`autoscaling/v2`)
  - PDB template baseline (`policy/v1`)
  - prod CD profile enables autoscaling/PDB and starts applications with 2 replicas
- CI/CD pipeline baseline:
  - CI executes unit tests and API integration test with Testcontainers + WebTestClient (`OrderLifecycleWebTestClientIT`).
  - CI generates JaCoCo reports for all seven application services and enforces aggregated platform instruction coverage `>= 80%`.
  - CD workflow (`.github/workflows/cd.yml`) builds and pushes service images to GHCR.
  - CD deploy path: Helm release to `edop-dev` and (with manual approval) to `edop-prod`.

## 3) Repro steps (k3d + Helm)

From repository root:

```powershell
.\infra\k8s\k3d-down.ps1
.\infra\k8s\k3d-up.ps1
```

If `mongodb` stays in `ImagePullBackOff` on local Windows + `k3d`, import `mongo:7` directly into node containerd and restart dependent deployments:

```powershell
docker exec k3d-edop-server-0 ctr -n k8s.io images pull docker.io/library/mongo:7
docker exec k3d-edop-agent-0 ctr -n k8s.io images pull docker.io/library/mongo:7
kubectl delete pod -n edop-dev -l app.kubernetes.io/name=mongodb
kubectl rollout status deployment/mongodb -n edop-dev --timeout=180s
kubectl rollout restart deployment/product-service -n edop-dev
kubectl rollout restart deployment/notification-service -n edop-dev
```

Validate the rollout and gateway endpoints:

```powershell
kubectl rollout status deployment/api-gateway -n edop-dev --timeout=180s
kubectl rollout status deployment/product-service -n edop-dev --timeout=180s
kubectl rollout status deployment/notification-service -n edop-dev --timeout=180s
.\infra\k8s\smoke-check.ps1
```

## 4) Verification evidence

Verification completed on `2026-03-27`.

- Helm release `edop` in namespace `edop-dev` completed with status `deployed`
- direct node-level `ctr` pull for `mongo:7` was executed successfully on:
  - `k3d-edop-server-0`
  - `k3d-edop-agent-0`
- rollout completed successfully for:
  - `mongodb`
  - `api-gateway`
  - `product-service`
  - `notification-service`
- `notification-service` rollout completed successfully in `k3d` after aligning Kubernetes probes to `/health`
- gateway smoke-check passed for:
  - `http://localhost:8080/actuator/health`
  - `http://localhost:8080/swagger-ui.html`
  - `http://localhost:8080/api-docs/auth`
  - `http://localhost:8080/api-docs/user`
  - `http://localhost:8080/api-docs/product`
  - `http://localhost:8080/api-docs/inventory`
  - `http://localhost:8080/api-docs/order`
  - `http://localhost:8080/api-docs/notification`
- final smoke-check result: `Smoke checks passed.`

## 5) Notes

- Local `k3d` verification on Windows exposed an image-import issue for `mongo:7`; direct node-level `ctr` pull was used as the working local recovery path.
- If a previous manual `kubectl patch` was used for `notification-service`, Helm server-side apply can require `helm upgrade ... --force-conflicts` to reclaim ownership of probe fields.
- This issue affects local cluster bootstrap or local field ownership only and does not change deployed application behavior after the stack is up.
