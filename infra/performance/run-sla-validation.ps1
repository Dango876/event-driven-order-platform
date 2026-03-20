param(
    [string]$BaseUrl = "http://host.docker.internal:8080",
    [string]$Duration = "5m",
    [int]$TargetRps = 500,
    [int]$PreAllocatedVus = 200,
    [int]$MaxVus = 1000
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$perfDir = Join-Path $root "performance"
$scriptPath = Join-Path $perfDir "k6-gateway-sla.js"
$summaryPath = Join-Path $perfDir "k6-sla-summary.json"

if (-not (Test-Path $scriptPath)) {
    throw "k6 SLA script not found: $scriptPath"
}

$volumePath = (Resolve-Path $perfDir).Path

docker version | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker daemon is not available. Start Docker Desktop and retry."
}

Write-Host "Running SLA validation profile..."
Write-Host "BaseUrl          : $BaseUrl"
Write-Host "Duration         : $Duration"
Write-Host "Target RPS       : $TargetRps"
Write-Host "Pre-allocated VUs: $PreAllocatedVus"
Write-Host "Max VUs          : $MaxVus"

docker run --rm `
    -i `
    -v "${volumePath}:/scripts" `
    grafana/k6 run `
    -e "BASE_URL=$BaseUrl" `
    -e "DURATION=$Duration" `
    -e "TARGET_RPS=$TargetRps" `
    -e "PRE_ALLOCATED_VUS=$PreAllocatedVus" `
    -e "MAX_VUS=$MaxVus" `
    --summary-export /scripts/k6-sla-summary.json `
    /scripts/k6-gateway-sla.js

if ($LASTEXITCODE -ne 0) {
    throw "k6 SLA validation run failed"
}

if (-not (Test-Path $summaryPath)) {
    throw "k6 SLA summary was not generated: $summaryPath"
}

$summary = Get-Content -Raw $summaryPath | ConvertFrom-Json

function Get-MetricField {
    param(
        [Parameter(Mandatory = $true)]$Metric,
        [Parameter(Mandatory = $true)][string]$Field
    )

    if ($null -ne $Metric.PSObject.Properties[$Field]) {
        return $Metric.$Field
    }

    if ($null -ne $Metric.PSObject.Properties["values"] -and $null -ne $Metric.values.PSObject.Properties[$Field]) {
        return $Metric.values.$Field
    }

    throw "Metric field '$Field' not found"
}

$p95 = [double](Get-MetricField -Metric $summary.metrics.http_req_duration -Field 'p(95)')
$failedRate = [double](Get-MetricField -Metric $summary.metrics.http_req_failed -Field 'value')
$checkRate = [double](Get-MetricField -Metric $summary.metrics.checks -Field 'value')
$rps = [double](Get-MetricField -Metric $summary.metrics.http_reqs -Field 'rate')

$thresholdFailRate = $failedRate -lt 0.01
$thresholdP95 = $p95 -lt 150.0
$thresholdChecks = $checkRate -gt 0.99
$thresholdRps = $rps -ge ([double]$TargetRps * 0.95)

Write-Host ""
Write-Host "k6 SLA summary:"
Write-Host ("  request rate     : {0} req/s" -f [Math]::Round($rps, 2))
Write-Host ("  p95 latency      : {0} ms" -f [Math]::Round($p95, 2))
Write-Host ("  failed rate      : {0}%" -f [Math]::Round(($failedRate * 100.0), 4))
Write-Host ("  checks pass rate : {0}%" -f [Math]::Round(($checkRate * 100.0), 4))
Write-Host ""
Write-Host "Thresholds:"
Write-Host ("  achieved rate >= 95% of target ({0} rps): {1}" -f $TargetRps, $(if ($thresholdRps) { "PASS" } else { "FAIL" }))
Write-Host ("  http_req_failed < 1%                     : {0}" -f $(if ($thresholdFailRate) { "PASS" } else { "FAIL" }))
Write-Host ("  http_req_duration p95 < 150ms            : {0}" -f $(if ($thresholdP95) { "PASS" } else { "FAIL" }))
Write-Host ("  checks pass rate > 99%                   : {0}" -f $(if ($thresholdChecks) { "PASS" } else { "FAIL" }))
Write-Host ""
Write-Host "Detailed summary: $summaryPath"
