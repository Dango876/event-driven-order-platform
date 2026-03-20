# Performance SLA Evidence (k8s-like)

## Goal

Produce long-run SLA evidence artifact in a k8s-like setup and archive it for acceptance docs.

## Prerequisites

- k3d stack is up: `.\infra\k8s\k3d-up.ps1`
- gateway reachable: `http://localhost:8080`
- Docker Desktop running

## Run

From repository root:

```powershell
.\infra\performance\run-sla-k8s-evidence.ps1
```

Recommended long-run profile:

```powershell
.\infra\performance\run-sla-k8s-evidence.ps1 -Duration 10m -TargetRps 500 -PreAllocatedVus 300 -MaxVus 1500
```

## Produced artifacts

The script stores timestamped evidence under:

- `infra/performance/evidence/k6-sla-summary-<timestamp>.json`
- `infra/performance/evidence/k6-sla-evidence-<timestamp>.md`

## Acceptance usage

1. Run the script on k3d environment.
2. Take the latest `k6-sla-evidence-<timestamp>.md`.
3. Add its key metrics and path/link into:
   - `docs/acceptance-checklist.md`
   - `docs/submission-report.md`

