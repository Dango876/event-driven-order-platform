# Alert Routing (Governance Baseline)

## Goal

Deliver fired alerts from Alertmanager to external HTTP endpoints with severity-based routing.

## Default local behavior

- Alertmanager routes:
  - `severity=critical` -> receiver `oncall-critical`
  - `severity=warning` -> receiver `external-webhook`
  - unmatched alerts -> receiver `default`
- Webhook URL sources:
  - warning/default route: `ALERT_WEBHOOK_URL`
  - critical route: `ALERT_ONCALL_WEBHOOK_URL`
- Default values in `docker-compose.yaml`:
  - both default to `http://alert-webhook-sink:8080/alerts`
- Local sink UI endpoint:
  - `http://localhost:8088`

This gives a visible local proof that alerts are routed out of Alertmanager.

## Use a real external endpoint

In PowerShell (before `dev-up`), configure warning and critical receivers separately:

```powershell
$env:ALERT_WEBHOOK_URL = "https://ops-webhook.example/warning-alerts"
$env:ALERT_ONCALL_WEBHOOK_URL = "https://oncall-webhook.example/critical-alerts"
.\dev-down.ps1
.\dev-up.ps1
```

## Quick verification

1. Confirm Alertmanager is up:
   - `http://localhost:9093/-/ready`
2. Trigger a warning test alert:

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

3. Trigger a critical test alert (same payload, `severity = "critical"`).

4. Observe delivery:
   - local default: open `http://localhost:8088` and check received requests
   - external endpoints: verify warning and critical routing in receiver-side logs/events
