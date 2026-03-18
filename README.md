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
- Infra containers are up (Redpanda, Schema Registry, Postgres, MongoDB, Redis).
- Services are healthy.
- Gateway is available at `http://localhost:8080`.
- Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

## Stop local stack

PowerShell:

```powershell
.\dev-down.ps1
```

GNU Make:

```powershell
make dev-down
```

## Smoke check

```powershell
.\infra\k8s\smoke-check.ps1
```

## Order lifecycle check

```powershell
.\infra\k8s\order-lifecycle-check.ps1
```

## Additional docs

- `docs/acceptance-checklist.md`
- `docs/k3d-helm-local-run.md`
- `docs/order-service-local-verification.md`
- `docs/swagger-openapi-endpoints.md`
