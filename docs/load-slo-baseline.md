# Load/SLO Baseline (Local)

## Goal

Run a repeatable local gateway load test and capture baseline SLO metrics:

- error rate
- p95 latency
- request throughput

## Prerequisites

- local stack is running (`.\dev-up.ps1`)
- Docker Desktop is running

## Run

From repository root:

```powershell
.\infra\performance\run-load-baseline.ps1
```

Optional custom run:

```powershell
.\infra\performance\run-load-baseline.ps1 -Duration 90s -Vus 30 -BaseUrl http://host.docker.internal:8080
```

## What is tested

k6 sends GET requests to:

- `/actuator/health`
- `/swagger-ui.html`
- `/api-docs/order`

## SLO thresholds (baseline)

- `http_req_failed < 1%`
- `http_req_duration p95 < 750ms`
- `checks pass rate > 99%`

## Artifacts

- k6 script: `infra/performance/k6-gateway-baseline.js`
- run helper: `infra/performance/run-load-baseline.ps1`
- exported summary: `infra/performance/k6-summary.json`

## Notes

- This is a local baseline, not a full production capacity test.
- For CI gating, run it with fixed `Duration` and `Vus` and archive `k6-summary.json` as an artifact.
