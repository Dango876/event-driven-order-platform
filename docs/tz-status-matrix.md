# Technical Specification Status Matrix

Date: 2026-03-27

## Purpose

This document maps the full technical specification to the current repository state and to the latest local verification results on a Windows laptop with Docker Desktop, WSL2, k3d, and Helm.

It is intentionally strict:

- `PASS` means the requirement is backed by repository evidence and/or reproducible local verification.
- `PARTIAL` means the baseline is implemented, but full production-grade or full end-to-end proof is not yet complete.
- `BLOCKED` means the requirement is not currently provable on the local laptop setup or is not yet completed.
- `N/A` means planning/background material rather than an acceptance item.

## Evidence scope used for this matrix

- `docs/acceptance-checklist.md`
- `docs/submission-report.md`
- `.github/workflows/ci.yml`
- `.github/workflows/security.yml`
- local validation performed on 2026-03-27:
  - gateway health and API reachability
  - k3d + Helm deployment and local `mongo:7` recovery path
  - notification-service rollout stabilization on `/health`
  - platform-wide JaCoCo aggregation across all service modules

## Status matrix

| Spec area | Status | Evidence | Notes |
| --- | --- | --- | --- |
| 1. Introduction / scope statement | N/A | Technical description only | Informational section, not an acceptance gate. |
| 2. Project goals | PARTIAL | Overall repository structure and docs | Goals are mostly reflected by the implementation, but goals themselves are not directly "pass/fail" artifacts. |
| 3. Domain model (small e-commerce order platform) | PASS | `docs/submission-report.md`, `docs/acceptance-checklist.md` | Services for catalog, users, orders, inventory, and notifications are present. |
| 4. Microservice architecture baseline | PARTIAL | `docs/submission-report.md`, Helm chart, service modules | Service decomposition is implemented. Internal gRPC/Kafka paths exist, but not every protocol edge has been re-verified end-to-end in the latest local session. |
| 4. Separate container per service + Kubernetes/Helm deployment | PASS | `docs/submission-report.md`, `infra/helm/`, `infra/k8s/k3d-up.ps1` | k3d + Helm deployment works as a baseline. |
| 4. Secrets in Kubernetes Secrets / sealed-secrets | PARTIAL | `docs/acceptance-checklist.md`, `infra/helm/edop/values.yaml` | Secret wiring baseline exists, including sealed-secrets support in values. Full production-grade secret management proof is still broader than local baseline. |
| 5.1 User registration by email/password with confirmation | PASS | `services/auth-service/src/main/java/com/procurehub/auth/api/AuthController.java`, `services/auth-service/src/main/java/com/procurehub/auth/service/EmailVerificationService.java`, `services/auth-service/src/main/resources/db/migration/V3__email_verification.sql` | Email confirmation flow is implemented in source. |
| 5.1 OAuth2 Login (Google, GitHub) | PARTIAL | `services/auth-service/src/main/resources/application.yml`, `services/auth-service/src/main/java/com/procurehub/auth/security/OAuth2AuthenticationSuccessHandler.java`, auth-service OAuth2 tests | OAuth2 baseline is implemented and unit-tested, but live provider end-to-end login was not re-verified locally in the latest session. |
| 5.1 Role management (`ROLE_USER`, `ROLE_MANAGER`, `ROLE_ADMIN`) | PASS | `docs/submission-report.md`, `services/product-service/src/main/java/com/procurehub/product/config/SecurityConfig.java`, `services/user-service/src/main/java/com/procurehub/user/config/SecurityConfig.java`, `services/order-service/src/main/java/com/procurehub/order/config/SecurityConfig.java` | Roles issued by `auth-service` are enforced across gateway-facing business endpoints; RBAC verification through gateway is documented in the submission report. |
| 5.2 Product CRUD for admins | PASS | `docs/submission-report.md`, `services/product-service/src/main/java/com/procurehub/product/config/SecurityConfig.java` | Product write operations are restricted to `ROLE_ADMIN`; this is part of the RBAC verification baseline captured on `2026-03-25`. |
| 5.2 Product publish/hide | PARTIAL | `product-service` source, `published` filtering support | Publish-state support exists in the product API/service layer, but no fresh acceptance run was captured for this item alone. |
| 5.2 Search + filters (`price range`, `category`, `text`) | PASS | `services/product-service/src/main/java/com/procurehub/product/api/ProductController.java`, `services/product-service/src/main/java/com/procurehub/product/service/ProductService.java` | Source-level evidence is explicit for `text`, `category`, `minPrice`, `maxPrice`, and `published`. |
| 5.3 Order placement (`ROLE_USER`) | PARTIAL | `docs/acceptance-checklist.md`, order lifecycle check | Order placement/lifecycle baseline works, but role-based acceptance for this exact endpoint path was not re-run separately in the latest session. |
| 5.3 Inventory check through Kafka request/response or gRPC | PARTIAL | `docs/submission-report.md`, service topology, order lifecycle success | The platform baseline uses internal integration paths, but the exact protocol proof for this requirement was not isolated in the latest run. |
| 5.3 Statuses `NEW -> RESERVED -> PAID -> SHIPPED -> COMPLETED / CANCELLED` | PASS | `docs/acceptance-checklist.md` | Order lifecycle smoke verification already recorded `RESERVED -> PAID -> SHIPPED -> COMPLETED` and rejected invalid reverse transition with `409`. |
| 5.3 Saga compensation on insufficient stock | PASS | `docs/submission-report.md`, `services/order-service/src/main/java/com/procurehub/order/kafka/InventoryEventsListener.java`, `services/order-service/src/main/java/com/procurehub/order/service/OrderService.java` | Fail-path verification is recorded: insufficient stock moves the order to `RESERVATION_FAILED`, and the reserved-event handler performs compensation-aware transition logic instead of logging-only behavior. |
| 5.4 Notification on order status change | PASS | `docs/submission-report.md`, `services/notification-service/src/main/java/com/procurehub/notification/service/NotificationDispatchService.java`, `services/notification-service/src/main/java/com/procurehub/notification/sender/EmailNotificationSender.java` | Real email delivery via Spring Mail was verified locally through Mailpit, and MongoDB persistence for notification records/user projections is documented. |
| 5.4 Rate limiting per user via Redis leaky bucket | PASS | `docs/acceptance-checklist.md`, `docs/notification-rate-limit.md` | Redis-backed per-user leaky bucket baseline is explicitly documented as passing. |
| 6. Performance: p95 REST latency <= 150ms at 500 RPS | PASS | Local k6 results on 2026-03-22 | Long-run in-cluster evidence passed at `500 RPS / 10m` with `p95 = 3.21 ms`, `0% failed`, `100% checks`, and archived evidence files under `infra/performance/evidence/`. |
| 6. Availability: 99.5% per month | BLOCKED | No realistic monthly proof on a single laptop | This cannot be credibly demonstrated on the current local setup. |
| 6. Scalability: scale each microservice up to x4 without downtime | PARTIAL | `docs/acceptance-checklist.md`, HPA/PDB/rolling update baseline | Scaling/no-downtime deployment baseline exists in Helm/CD, but a full live x4 proof has not been re-run as an acceptance artifact in the latest session. |
| 6. Security: OWASP Top-10, TLS 1.3, secrets via Vault/Secrets | PARTIAL | `.github/workflows/security.yml`, `docs/tls-secrets-k8s-baseline.md` | OWASP Dependency-Check and Trivy gates exist; TLS baseline exists; Kubernetes secrets baseline exists. Full Vault-grade production proof is broader than the current local baseline. |
| 6. Structured logs to Loki; tracing via OpenTelemetry + Jaeger | PASS | `docs/acceptance-checklist.md`, `docs/observability-local-run.md` | Loki, Promtail, Grafana, OTLP, and Jaeger baseline were verified locally. |
| 6. Monitoring via Prometheus + Grafana + alert rules | PASS | `docs/acceptance-checklist.md` | Prometheus, Grafana, Alertmanager, and alert rules are documented as passing. |
| 7. Kafka events/topics + Schema Registry + Avro baseline | PARTIAL | `docs/submission-report.md`, security notes, local k3d baseline | Redpanda + Schema Registry baseline exists and is used by the platform, but a fresh topic-by-topic acceptance artifact is not attached for every listed event contract. |
| 8. PostgreSQL 15 / MongoDB 7 / Redis 7 data storage usage | PASS | Service architecture, local stack composition, docs | Data store split by service role is implemented in the platform baseline. |
| 9.0 CI pipeline: checkstyle, tests, coverage >= 80%, Docker build, Helm dev release, manual prod approval | PASS | `.github/workflows/ci.yml`, `.github/workflows/cd.yml`, `.github/workflows/security.yml`, `docs/acceptance-checklist.md` | CI now generates JaCoCo reports for all seven application services and enforces aggregated platform instruction coverage `>= 80%`. |
| 9.1 Local deployment: `make dev-up` / `./dev-up.ps1` one-command stack | PASS | `docs/acceptance-checklist.md`, `docs/submission-report.md` | Compose-based one-command startup is already documented as passing. |
| 9.1 Local k3d test environment: `make k3d-up` / `./infra/k8s/k3d-up.ps1` | PASS | `docs/acceptance-checklist.md`, `docs/submission-report.md`, `infra/k8s/k3d-up.ps1`, `infra/helm/edop/values.yaml` | Fresh local verification on `2026-03-27` succeeded, including `mongo:7` recovery path, rollout completion, and final smoke-check. |
| 10. Sprint plan | N/A | Planning material only | Useful for tracking, not an acceptance gate. |
| 11. Acceptance: all services pass unit and integration tests; pipeline green | PASS | `docs/acceptance-checklist.md`, `.github/workflows/ci.yml` | All application services now participate in CI test/coverage verification; `order-service` keeps the API integration path and the platform-wide coverage gate is enforced in CI. |
| 11. Acceptance: local k3d cluster deployed | PASS | `docs/acceptance-checklist.md`, `docs/submission-report.md` | The cluster deploys locally, required workloads roll out successfully, and smoke-check passed on `2026-03-27`. |
| 11. Acceptance: order completes through full lifecycle without manual intervention | PASS | `docs/acceptance-checklist.md` | Already recorded as passing. |
| 11. Acceptance: Swagger UI reflects current OpenAPI and endpoints work | PASS | `docs/acceptance-checklist.md`, `docs/swagger-openapi-endpoints.md` | Gateway Swagger/OpenAPI baseline already documented as passing. |
| 11. Acceptance: OWASP Dependency-Check + Trivy show 0 critical vulnerabilities | PASS | `.github/workflows/security.yml`, `docs/acceptance-checklist.md` | Current security workflow is documented as green. |
| 11. Acceptance: full local stack starts with one command | PASS | `docs/acceptance-checklist.md` | `./dev-up.ps1` baseline is documented as passing. |
| 11. Acceptance: API checked through Swagger UI and generated OpenAPI YAML | PASS | `docs/acceptance-checklist.md`, `docs/swagger-openapi-endpoints.md` | This baseline is already part of the local acceptance docs. |

## Long-run SLA evidence

The long-run in-cluster SLA proof is now complete on the laptop.

### Confirmed passing local evidence

On 2026-03-22, the following k6 in-cluster validations were completed successfully:

- `100 RPS / 20s`: `PASS`
  - `p95 = 7.13 ms`
  - `0% failed`
  - `100% checks`
- `500 RPS / 2m`: `PASS`
  - `p95 = 3.97 ms`
  - `0% failed`
  - `100% checks`
- `500 RPS / 10m`: `PASS`
  - `request rate = 500.06 req/s`
  - `p95 = 3.21 ms`
  - `0% failed`
  - `100% checks`

Archived evidence:

- `infra/performance/evidence/k6-sla-summary-20260322-145202.json`
- `infra/performance/evidence/k6-sla-evidence-20260322-145202.md`

## Bottom line

### Safely claimable today

- The project now meets the documented local MVP / technical acceptance baseline.
- Functional, CI/CD, security-baseline, observability, local deployment, and long-run SLA requirements are covered in the current evidence scope.
- Long-run in-cluster SLA evidence at `500 RPS / 10m` is archived and reproducible from the project tooling.

### Not safe to overclaim yet

- Production-grade availability proof (`99.5%/month`) on a laptop
- Full production-grade on-call rollout with real receivers/escalation ownership

## Recommended next steps

1. If the formal submission demands it, document the remaining distinction between local acceptance evidence and production-grade availability/on-call operations.
2. Keep archived CI, k3d, and k6 evidence links up to date as new runs replace older green runs.
