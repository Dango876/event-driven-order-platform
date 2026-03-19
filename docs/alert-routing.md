# Alert Routing (External Webhook)

## Goal

Deliver fired alerts from Alertmanager to an external HTTP endpoint.

## Default local behavior

- Alertmanager receiver: `external-webhook`
- Webhook URL source: env var `ALERT_WEBHOOK_URL`
- Default value in `docker-compose.yaml`:
  - `http://alert-webhook-sink:8080/alerts`
- Local sink UI endpoint:
  - `http://localhost:8088`

This gives a visible local proof that alerts are routed out of Alertmanager.

## Use a real external endpoint

In PowerShell (before `dev-up`):

```powershell
$env:ALERT_WEBHOOK_URL = "https://your-webhook.example/alerts"
.\dev-down.ps1
.\dev-up.ps1
```

## Quick verification

1. Confirm Alertmanager is up:
   - `http://localhost:9093/-/ready`
2. Trigger a test alert:

```powershell
$body = @(
  @{
    labels = @{
      alertname = "ManualWebhookTest"
      severity  = "warning"
      job       = "manual-test"
    }
    annotations = @{
      summary = "Manual webhook routing test"
    }
  }
) | ConvertTo-Json -Depth 5

Invoke-WebRequest -Uri "http://localhost:9093/api/v2/alerts" -Method Post -ContentType "application/json" -Body $body -UseBasicParsing
```

3. Observe delivery:
   - local default: open `http://localhost:8088` and check received request
   - external endpoint: check receiver-side logs/events
