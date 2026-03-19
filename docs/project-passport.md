# Project Passport (MVP)

Date: 2026-03-19 (Europe/Moscow)
Repository: `Dango876/event-driven-order-platform`
Stage: MVP / acceptance baseline complete

## 1) Project summary

Event-driven order processing platform for small e-commerce:
- API gateway
- auth-service
- user-service
- product-service
- inventory-service
- order-service
- notification-service

Core stack:
- Java 17, Spring Boot 3, Spring Security 6
- Redpanda (Kafka API) + Schema Registry
- PostgreSQL, MongoDB, Redis
- Docker Compose + k3d + Helm
- Prometheus, Grafana, Loki, Jaeger, Alertmanager

## 2) What is implemented

- One-command local startup/stop:
  - `.\dev-up.ps1`
  - `.\dev-down.ps1`
- API gateway + Swagger UI + per-service OpenAPI routing.
- Order lifecycle flow with transition checks:
  - `RESERVED -> PAID -> SHIPPED -> COMPLETED`
  - invalid transition rejection (`409`) verified.
- Local observability baseline:
  - metrics (Prometheus), logs (Loki/Promtail), tracing (Jaeger), dashboards (Grafana).
- Alerting baseline:
  - Prometheus alert rules + Alertmanager routing + local webhook sink.
- Notification rate-limiting baseline:
  - Redis leaky bucket in `notification-service`.
- CI/CD baseline:
  - CI + Security workflows green.
  - CD workflow defined in `.github/workflows/cd.yml`.
  - API integration test with Testcontainers + WebTestClient in `order-service`.

## 3) Evidence links

- CI green run: [Run #23314850628](https://github.com/Dango876/event-driven-order-platform/actions/runs/23314850628)
- Security green run: [Run #23314850635](https://github.com/Dango876/event-driven-order-platform/actions/runs/23314850635)
- Latest CI runs page: [CI workflow](https://github.com/Dango876/event-driven-order-platform/actions/workflows/ci.yml)
- Latest Security page: [Security workflow](https://github.com/Dango876/event-driven-order-platform/actions/workflows/security.yml)
- CD workflow page: [cd.yml workflow](https://github.com/Dango876/event-driven-order-platform/actions/workflows/cd.yml)

Detailed acceptance evidence:
- `docs/acceptance-checklist.md`
- `docs/submission-report.md`
- `docs/observability-local-run.md`
- `docs/ci-cd-pipeline.md`

## 4) Quick reproduce

```powershell
.\dev-down.ps1
.\dev-up.ps1
.\infra\k8s\smoke-check.ps1
.\infra\k8s\order-lifecycle-check.ps1 -RequestTimeoutSec 90 -OrderCreateRetries 5 -RetryDelaySec 5
.\infra\performance\run-load-baseline.ps1 -Duration 20s -Vus 20
.\mvnw.cmd -B -ntp -U test
```

## 5) Current status vs TZ

- Mandatory acceptance baseline: completed.
- CI/Security baseline: completed and green.
- Local deploy/repro and observability baseline: completed.

## 6) Remaining production-hardening items (not blocking MVP)

- Long-run load/perf validation for full SLA envelope (500 RPS target window).
- Production alert routing governance (on-call escalation policy).
- Optional stretch features from plan (social OAuth login, advanced rate-limit tuning, etc.).
