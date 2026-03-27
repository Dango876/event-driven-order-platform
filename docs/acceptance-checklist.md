# Acceptance Checklist (Week 1/2 + Observability Baseline)

## Scope

This checklist closes Week 1 and Week 2 outcomes from the project plan:
- Week 1: project skeleton, CI baseline, Docker/Helm basics, runnable services.
- Week 2: auth + gateway + product/inventory CRUD baseline + Kafka/Schema Registry integration baseline.
- Observability baseline: Prometheus + Grafana + Loki + Alertmanager with local verification.

Date of latest verification: 2026-03-27 (Europe/Moscow).

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
- Note:
  - `.\dev-up.ps1` and `.\infra\k8s\k3d-up.ps1` are alternative local runtimes and should not be active at the same time because both expose the gateway on host port `8080`.
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
- `mvn -U -pl services/api-gateway,services/auth-service,services/user-service,services/product-service,services/inventory-service,services/order-service,services/notification-service -am org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report`
- local JDK 24 workaround for this workstation:
- `.\mvnw.cmd --% -B -ntp -U -Dnet.bytebuddy.experimental=true -pl services/api-gateway,services/auth-service,services/user-service,services/product-service,services/inventory-service,services/order-service,services/notification-service -am org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report`

Expected:
- Reactor build success.
- Tests executed across all application services.
- Aggregate service instruction coverage `>= 80%`.

Verification status:
- `PASS`.
- Latest platform-wide service coverage aggregation completed on `2026-03-27`.
- Aggregate instruction coverage across `api-gateway`, `auth-service`, `user-service`, `product-service`, `inventory-service`, `order-service`, and `notification-service`: `81.06%`.
- Local note:
- on this Windows workstation, ad-hoc local `auth-service` verification under JDK 24 required `-Dnet.bytebuddy.experimental=true` for Mockito inline support; in Windows PowerShell the safest form is `.\mvnw.cmd --% ...` so the `-D...` argument is passed to Maven literally.
  - CI workflow itself uses Java 17 and does not require that local-only flag.

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
- `user-service` exposes CRUD-style user management endpoints (`GET/POST/PUT/DELETE /users`).
- Observability and alerting baseline is available and verified locally.

## 6. Kubernetes acceptance artifact (k3d + Helm)

Verification status:
- `PASS` on local k3d + Helm run.

Evidence:
- `.\infra\k8s\k3d-down.ps1`
- `.\infra\k8s\k3d-up.ps1`
- direct node-level recovery for `mongo:7` on `k3d-edop-server-0` and `k3d-edop-agent-0` when local Windows image import left `mongodb` in `ImagePullBackOff`
- `.\infra\k8s\smoke-check.ps1` passed
- `.\infra\k8s\order-lifecycle-check.ps1` passed

## 7. CI and security evidence (green)

Verification status:
- `PASS` for CI workflow and security workflow.
- CI (green): `https://github.com/Dango876/event-driven-order-platform/actions/runs/23314850628`
- Security Scan (green, latest workflow view): `https://github.com/Dango876/event-driven-order-platform/actions/runs/23314850635`

Notes:
- OWASP step is configured with NVD API key usage and retry/delay hardening for CI stability.
- Trivy gate passed after Avro update to fixed version (`1.11.4`).
- CI includes API integration coverage with Testcontainers + WebTestClient:
  - `services/order-service/src/test/java/com/procurehub/order/api/OrderLifecycleWebTestClientIT.java`
  - `services/order-service/pom.xml` includes `**/*IT.java` in Surefire test includes.
- CI also aggregates JaCoCo instruction coverage across all seven application services and enforces platform-wide threshold `>= 80%`.
- CD baseline workflow is defined in:
  - `.github/workflows/cd.yml`
  - stages: Docker build/push to GHCR -> Helm release to `edop-dev` -> manual approval gate (`prod` environment) -> Helm release to `edop-prod`.
  - required repository secrets for deploy stages:
    - `KUBE_CONFIG_DEV`
    - `KUBE_CONFIG_PROD`

## 8. Observability and alerting baseline (local)

Verification status:
- `PASS` on local Docker Compose stack (`.\dev-up.ps1`).

Covered:
- Metrics:
  - Prometheus endpoint: `http://localhost:9090`
  - Service `/actuator/prometheus` scraping for gateway and all services.
- Tracing:
  - Jaeger UI: `http://localhost:16686`
  - OTLP endpoint for services: `http://localhost:4318/v1/traces`
- Logs:
  - Loki readiness: `http://localhost:3100/ready`
  - Promtail collection from `.logs/*.app.log`
  - Grafana Explore query verified: `{job="edop-local"}`
- Dashboard:
  - Grafana: `http://localhost:3000`
  - Preloaded dashboard: `Dashboards -> EDOP Local -> EDOP Local Observability`
  - Panels verified: `HTTP RPS`, `HTTP 5xx Rate`, `HTTP p95 Latency (ms)`, `Recent ERROR Logs`.
- Alerting:
  - Alertmanager readiness: `http://localhost:9093/-/ready`
  - Severity-based routing:
    - `warning` -> `ALERT_WEBHOOK_URL`
    - `critical` -> `ALERT_ONCALL_WEBHOOK_URL`
  - Local webhook sink endpoint: `http://localhost:8088`
  - Alert rules present in Prometheus:
    - `EdopServiceDown`
    - `EdopHigh5xxRate`
    - `EdopHighP95Latency`

Evidence:
- Local verification commands:
  - `(Invoke-WebRequest http://localhost:9090/-/ready -UseBasicParsing).StatusCode`
  - `(Invoke-WebRequest http://localhost:9093/-/ready -UseBasicParsing).StatusCode`
  - `(Invoke-WebRequest http://localhost:16686 -UseBasicParsing).StatusCode`
  - `(Invoke-WebRequest http://localhost:8088 -UseBasicParsing).StatusCode`
  - `(Invoke-WebRequest http://localhost:3000/api/health -UseBasicParsing).StatusCode`
  - `(Invoke-WebRequest http://localhost:3100/ready -UseBasicParsing).Content`
  - `Get-ChildItem .\.logs\*.app.log`
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

Additional SLA profile tooling:
- `infra/performance/k6-gateway-sla.js`
- `infra/performance/run-sla-validation.ps1`
- `docs/performance-sla-validation.md`
- `infra/performance/run-sla-k8s-evidence.ps1`
- `docs/performance-sla-k8s-evidence.md`

SLA profile evidence (`-Duration 5m -TargetRps 500`):
- request rate: `499.42 req/s` (`PASS`, >= 95% of target)
- p95 latency: `3.82ms` (`PASS`, < 150ms)
- failed rate: `0%` (`PASS`, < 1%)
- checks pass rate: `100%` (`PASS`, > 99%)

Long-run SLA evidence (`-Duration 10m -TargetRps 500`):
- verification date: `2026-03-22`
- command:
  - `.\infra\performance\run-sla-k8s-evidence.ps1 -Runner k8s -BaseUrl http://api-gateway:8080 -Duration 10m -TargetRps 500 -PreAllocatedVus 200 -MaxVus 1000 -WarmupDuration 20s -WarmupTargetRps 100 -WarmupPreAllocatedVus 50 -WarmupMaxVus 200`
- observed result:
  - request rate: `500.06 req/s` (`PASS`, >= 95% of target)
  - p95 latency: `3.21ms` (`PASS`, < 150ms)
  - failed rate: `0%` (`PASS`, < 1%)
  - checks pass rate: `100%` (`PASS`, > 99%)
- archived evidence:
  - `infra/performance/evidence/k6-sla-summary-20260322-145202.json`
  - `infra/performance/evidence/k6-sla-evidence-20260322-145202.md`

## 10. Notification rate-limiting baseline (Redis leaky bucket)

Verification status:
- `PASS` for implementation baseline (`notification-service`).

Covered:
- Redis-backed leaky bucket per user:
  - bucket level leaks over time (`leak-per-second`), capacity-limited.
  - when capacity is exceeded, notification delivery is skipped and logged as warning.
- `order.created` events:
  - store `orderId -> userId` mapping in Redis and apply user bucket immediately.
- `order.status-changed` events:
  - resolve `userId` via `orderId` mapping and apply user bucket.
  - fallback bucket by `orderId` if mapping is unavailable.

Config:
- `NOTIFICATION_RATE_LIMIT_ENABLED` (default `true`)
- `NOTIFICATION_RATE_LIMIT_CAPACITY` (default `20`)
- `NOTIFICATION_RATE_LIMIT_LEAK_PER_SECOND` (default `5`)
- `REDIS_HOST` / `REDIS_PORT`

## 11. OAuth2 login baseline hardening

Verification status:
- `PASS` for auth-service implementation baseline.

Covered:
- OAuth2 client registrations for Google and GitHub in `auth-service`.
- OAuth2 success handler issues platform JWT/refresh tokens.
- GitHub fallback mapping (`login@users.noreply.github.com`) when provider email is unavailable.
- New users created via OAuth2 publish `user.created` event to keep downstream projections consistent.
- Unit tests:
  - `AuthServiceOAuth2LoginTest`
  - `OAuth2AuthenticationSuccessHandlerTest`

## 12. TLS + Secrets k8s baseline

Verification status:
- `PASS` for deployment baseline implementation.

Covered:
- Optional ingress template for `api-gateway` with TLS support.
- TLS policy annotations include nginx TLS 1.3 protocol restriction.
- CD workflow can optionally upsert TLS secrets from GitHub Secrets:
  - `TLS_CERT_PEM_DEV` / `TLS_KEY_PEM_DEV`
  - `TLS_CERT_PEM_PROD` / `TLS_KEY_PEM_PROD`
- Prod CD deploy enables ingress TLS and uses `edop-gateway-tls`.
- Helm deployment template supports optional secret refs via `envFromSecrets`.

Evidence:
- `infra/helm/edop/templates/ingress.yaml`
- `infra/helm/edop/values.yaml`
- `infra/helm/edop/templates/deployments.yaml`
- `.github/workflows/cd.yml`
- `docs/tls-secrets-k8s-baseline.md`

## 13. Redis distributed lock baseline (Redlock pattern)

Verification status:
- `PASS` for implementation baseline in `inventory-service`.

Covered:
- Redis-backed distributed lock manager for inventory write operations.
- Lock ownership-safe release via Lua script.
- Configurable timeout/retry/lease/fail-open behavior.
- HTTP mapping for lock acquisition failures -> `503`.
- Unit tests:
  - `InventoryServiceDistributedLockTest`

Evidence:
- `services/inventory-service/src/main/java/com/procurehub/inventory/service/RedisDistributedLockService.java`
- `services/inventory-service/src/main/java/com/procurehub/inventory/service/InventoryService.java`
- `services/inventory-service/src/main/java/com/procurehub/inventory/api/error/GlobalExceptionHandler.java`
- `services/inventory-service/src/test/java/com/procurehub/inventory/service/InventoryServiceDistributedLockTest.java`
- `docs/redis-distributed-locks.md`

## 14. Kubernetes scaling + no-downtime rollout baseline

Verification status:
- `PASS` for implementation baseline in Helm + CD production wiring.

Covered:
- Default rolling strategy for applications:
  - `RollingUpdate`
  - `maxUnavailable: 0`
  - `maxSurge: 1`
  - rollout safety defaults (`minReadySeconds`, `progressDeadlineSeconds`, `revisionHistoryLimit`)
- HPA template (`autoscaling/v2`) for applications with default target profile:
  - `minReplicas: 2`
  - `maxReplicas: 4`
  - CPU target utilization
- PDB template (`policy/v1`) for applications (`minAvailable` baseline).
- CD `deploy-prod` enables autoscaling + PDB baseline and sets initial replicas to `2` for all applications.

Evidence:
- `infra/helm/edop/templates/deployments.yaml`
- `infra/helm/edop/templates/hpa.yaml`
- `infra/helm/edop/templates/pdb.yaml`
- `infra/helm/edop/values.yaml`
- `.github/workflows/cd.yml`
- `docs/k8s-scaling-availability-baseline.md`

## 15. Remaining acceptance items (outside current baseline)

Long-run SLA evidence on k3d is now complete and archived:
- command completed successfully on `2026-03-22`
- evidence files:
  - `infra/performance/evidence/k6-sla-summary-20260322-145202.json`
  - `infra/performance/evidence/k6-sla-evidence-20260322-145202.md`

Still outside the current local baseline:
- Operational on-call process rollout (schedule ownership/escalation policy with real receivers).

