# Event-Driven Order Platform

Local development and validation commands for the project.

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
- `docs/swagger-openapi-endpoints.md`
- `docs/tls-secrets-k8s-baseline.md`
