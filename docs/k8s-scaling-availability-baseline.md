# Kubernetes Scaling + Availability Baseline

## Goal

Provide a production-oriented baseline for:
- zero-downtime rolling updates,
- horizontal autoscaling up to x4 replicas,
- disruption protection during node maintenance.

## Helm implementation

Chart: `infra/helm/edop`

Implemented templates:
- `templates/deployments.yaml`
  - default application rollout strategy:
    - `RollingUpdate`
    - `maxUnavailable: 0`
    - `maxSurge: 1`
  - rollout safety defaults:
    - `minReadySeconds`
    - `progressDeadlineSeconds`
    - `revisionHistoryLimit`
  - default app resources (requests/limits) for HPA signal quality.
- `templates/hpa.yaml`
  - `autoscaling/v2` HPA per enabled application.
  - default targets:
    - `minReplicas: 2`
    - `maxReplicas: 4`
    - CPU utilization target.
- `templates/pdb.yaml`
  - `policy/v1` PDB per enabled application.
  - default `minAvailable: 1`.

Values:
- `rolloutDefaults.*`
- `resourcesDefaults.applications.*`
- `autoscalingDefaults.*`
- `pdbDefaults.*`

## CD production wiring

`.github/workflows/cd.yml` (`deploy-prod` job) now enables:
- `autoscalingDefaults.enabled=true`
- `autoscalingDefaults.minReplicas=2`
- `autoscalingDefaults.maxReplicas=4`
- `pdbDefaults.enabled=true`
- initial app replicas set to `2` for all application deployments.

This gives a stable baseline where each service can scale up to x4 and rolling updates avoid planned downtime.

## Validation

Template validation:

```powershell
helm template edop .\infra\helm\edop -f .\infra\helm\edop\values.yaml > $null
```

Optional prod-like render:

```powershell
helm template edop .\infra\helm\edop `
  -f .\infra\helm\edop\values.yaml `
  --set autoscalingDefaults.enabled=true `
  --set pdbDefaults.enabled=true `
  --set autoscalingDefaults.minReplicas=2 `
  --set autoscalingDefaults.maxReplicas=4 > $null
```

Runtime checks (cluster):

```powershell
kubectl get deploy,hpa,pdb -n edop-prod
kubectl describe hpa api-gateway -n edop-prod
kubectl rollout status deployment/api-gateway -n edop-prod
```
