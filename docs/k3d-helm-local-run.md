# k3d + Helm Local Runbook

## Prerequisites

- Docker Desktop is running
- `k3d`, `kubectl`, `helm`, `make` are installed and available in `PATH`
- Port `8080` is free on host (gateway will be exposed there)

If `make` is not installed, use scripts directly:
- `.\infra\k8s\k3d-up.ps1`
- `.\infra\k8s\k3d-down.ps1`
- `.\infra\k8s\inject-secrets.ps1`
- `.\infra\k8s\smoke-check.ps1`
- `.\infra\k8s\order-lifecycle-check.ps1`

## 1. Optional: inject local secrets

Create `.env` files in `.secrets/dev` (see `.secrets/dev/README.md`) and run:

```powershell
make inject-secrets
```

Example secret files:
- `.secrets/dev/auth-service.env` (JWT/OAuth secrets)
- `.secrets/dev/api-gateway.env` (gateway runtime secrets, if needed)

## 2. Start cluster and deploy stack

```powershell
make k3d-up
```

Do not run `.\dev-up.ps1` while the local `k3d` stack is active. Both runtimes expose the gateway on host port `8080`.

What this does:
- creates `k3d` cluster `edop` (if missing)
- builds 7 service images from local source
- imports images into cluster
- deploys infra + services with Helm chart `infra/helm/edop`
- exposes API Gateway on `http://localhost:8080`
- keeps ingress TLS disabled by default for local k3d baseline
- keeps HPA/PDB disabled by default in local baseline (enabled in prod CD profile)

## 3. Validate deployment

```powershell
kubectl get pods -n edop-dev
Invoke-RestMethod http://localhost:8080/actuator/health
start http://localhost:8080/swagger-ui.html
make k3d-smoke
make order-smoke
```

Expected:
- all pods `Running` / `READY 1/1`
- health returns `status = UP`
- Swagger UI opens
- smoke checks pass with HTTP 200
- order lifecycle smoke passes

## 4. Stop and clean

```powershell
make k3d-down
```

## Troubleshooting

- If `make k3d-up` fails on port bind, free host `8080` and retry.
- If pods restart often, inspect logs:

```powershell
kubectl logs -n edop-dev deployment/api-gateway --tail=200
kubectl logs -n edop-dev deployment/order-service --tail=200
```

- Re-run Helm deployment without recreating cluster:

```powershell
helm upgrade --install edop .\infra\helm\edop -n edop-dev -f .\infra\helm\values\dev.yaml --wait --timeout 10m
```

- If local Windows + `k3d` leaves `mongodb` in `ImagePullBackOff`, import `mongo:7` directly into node containerd and restart dependent deployments:

```powershell
docker exec k3d-edop-server-0 ctr -n k8s.io images pull docker.io/library/mongo:7
docker exec k3d-edop-agent-0 ctr -n k8s.io images pull docker.io/library/mongo:7
kubectl delete pod -n edop-dev -l app.kubernetes.io/name=mongodb
kubectl rollout status deployment/mongodb -n edop-dev --timeout=180s
kubectl rollout restart deployment/product-service -n edop-dev
kubectl rollout restart deployment/notification-service -n edop-dev
kubectl rollout status deployment/product-service -n edop-dev --timeout=180s
kubectl rollout status deployment/notification-service -n edop-dev --timeout=180s
.\infra\k8s\smoke-check.ps1
```

- If `notification-service` was manually patched earlier and Helm reports server-side apply conflicts on probe fields, rerun upgrade with conflict takeover:

```powershell
helm upgrade edop .\infra\helm\edop -n edop-dev --force-conflicts
```

- If `redpanda` / `schema-registry` are stuck in `ContainerCreating` on Windows:

```powershell
docker pull docker.redpanda.com/redpandadata/redpanda:v24.1.9
docker pull confluentinc/cp-schema-registry:7.6.1
kubectl rollout restart deployment/redpanda -n edop-dev
kubectl rollout restart deployment/schema-registry -n edop-dev
kubectl wait -n edop-dev --for=condition=Available deployment/redpanda --timeout=600s
kubectl wait -n edop-dev --for=condition=Available deployment/schema-registry --timeout=600s
```
