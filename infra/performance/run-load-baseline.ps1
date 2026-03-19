param(
    [string]$BaseUrl = "http://host.docker.internal:8080",
    [string]$Duration = "60s",
    [int]$Vus = 20
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$perfDir = Join-Path $root "performance"
$scriptPath = Join-Path $perfDir "k6-gateway-baseline.js"
$summaryPath = Join-Path $perfDir "k6-summary.json"

if (-not (Test-Path $scriptPath)) {
    throw "k6 script not found: $scriptPath"
}

$volumePath = (Resolve-Path $perfDir).Path

docker version | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker daemon is not available. Start Docker Desktop and retry."
}

Write-Host "Running k6 baseline load test..."
Write-Host "BaseUrl  : $BaseUrl"
Write-Host "Duration : $Duration"
Write-Host "VUs      : $Vus"

docker run --rm `
    -i `
    -v "${volumePath}:/scripts" `
    grafana/k6 run `
    -e "BASE_URL=$BaseUrl" `
    -e "DURATION=$Duration" `
    -e "VUS=$Vus" `
    --summary-export /scripts/k6-summary.json `
    /scripts/k6-gateway-baseline.js

if ($LASTEXITCODE -ne 0) {
    throw "k6 baseline run failed"
}

if (-not (Test-Path $summaryPath)) {
    throw "k6 summary was not generated: $summaryPath"
}

$summary = Get-Content -Raw $summaryPath | ConvertFrom-Json

# k6 summary format differs between versions:
# - new format uses direct fields (e.g. metric.value, metric.count, metric.'p(95)')
# - older format uses metric.values.<field>
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

$p95 = Get-MetricField -Metric $summary.metrics.http_req_duration -Field 'p(95)'
$failedRate = Get-MetricField -Metric $summary.metrics.http_req_failed -Field 'value'
$checkRate = Get-MetricField -Metric $summary.metrics.checks -Field 'value'
$iterations = Get-MetricField -Metric $summary.metrics.iterations -Field 'count'
$rps = Get-MetricField -Metric $summary.metrics.http_reqs -Field 'rate'

$thresholdFailRate = [double]$failedRate -lt 0.01
$thresholdP95 = [double]$p95 -lt 750.0
$thresholdChecks = [double]$checkRate -gt 0.99

Write-Host ""
Write-Host "k6 summary:"
Write-Host ("  iterations       : {0}" -f [Math]::Round([double]$iterations, 0))
Write-Host ("  request rate     : {0} req/s" -f [Math]::Round([double]$rps, 2))
Write-Host ("  p95 latency      : {0} ms" -f [Math]::Round([double]$p95, 2))
Write-Host ("  failed rate      : {0}%" -f [Math]::Round(([double]$failedRate * 100.0), 4))
Write-Host ("  checks pass rate : {0}%" -f [Math]::Round(([double]$checkRate * 100.0), 4))
Write-Host ""
Write-Host "Thresholds:"
Write-Host ("  http_req_failed < 1%         : {0}" -f ($(if ($thresholdFailRate) { "PASS" } else { "FAIL" })))
Write-Host ("  http_req_duration p95 < 750ms: {0}" -f ($(if ($thresholdP95) { "PASS" } else { "FAIL" })))
Write-Host ("  checks pass rate > 99%       : {0}" -f ($(if ($thresholdChecks) { "PASS" } else { "FAIL" })))
Write-Host ""
Write-Host "Detailed summary: $summaryPath"
