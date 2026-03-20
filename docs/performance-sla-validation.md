# Performance SLA Validation (500 RPS Profile)

## Goal

Run a repeatable gateway load profile aligned with the target non-functional requirement:

- p95 latency <= 150 ms
- sustained load target: 500 requests/second
- error rate < 1%

## Prerequisites

- local stack is running (`.\dev-up.ps1`)
- Docker Desktop is running

## Run

From repository root:

```powershell
.\infra\performance\run-sla-validation.ps1
```

Optional tuned run:

```powershell
.\infra\performance\run-sla-validation.ps1 -Duration 10m -TargetRps 500 -PreAllocatedVus 300 -MaxVus 1500
```

## What is tested

k6 sends traffic through gateway to lightweight availability/docs routes:

- `/actuator/health`
- `/api-docs/order`
- `/api-docs/product`

Profile uses `constant-arrival-rate` to target fixed RPS.

## Pass criteria in script output

- achieved rate >= 95% of target RPS
- `http_req_failed < 1%`
- `http_req_duration p95 < 150ms`
- `checks pass rate > 99%`

## Artifacts

- k6 profile: `infra/performance/k6-gateway-sla.js`
- run helper: `infra/performance/run-sla-validation.ps1`
- exported summary: `infra/performance/k6-sla-summary.json`

## Notes

- This profile is intended for repeatable validation and trend tracking.
- For strict acceptance evidence, archive `k6-sla-summary.json` for each run.
- If local hardware cannot hold 500 RPS, run the same profile on a stronger host or k8s dev environment and keep the artifact link in acceptance docs.
