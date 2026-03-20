param(
    [string]$BaseUrl = "http://host.docker.internal:8080",
    [string]$Duration = "10m",
    [int]$TargetRps = 500,
    [int]$PreAllocatedVus = 300,
    [int]$MaxVus = 1500,
    [string]$DockerNetwork = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$perfDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$runnerPath = Join-Path $perfDir "run-sla-validation.ps1"
$summaryPath = Join-Path $perfDir "k6-sla-summary.json"
$evidenceDir = Join-Path $perfDir "evidence"

if (-not (Test-Path $runnerPath)) {
    throw "SLA runner not found: $runnerPath"
}

New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null

# run-sla-validation.ps1 executes k6 in Docker. If the caller passes localhost,
# k6 would try to hit the container itself, so we normalize to host.docker.internal.
if ($BaseUrl -match "^https?://localhost(?::\d+)?$") {
    $normalizedBaseUrl = $BaseUrl -replace "localhost", "host.docker.internal"
    Write-Warning "BASE_URL '$BaseUrl' points to container-local localhost for dockerized k6. Using '$normalizedBaseUrl' instead."
    $BaseUrl = $normalizedBaseUrl
}

Write-Host "Running k8s-like SLA evidence profile..."
Write-Host "BaseUrl  : $BaseUrl"
Write-Host "Duration : $Duration"
Write-Host "TargetRps: $TargetRps"
Write-Host ("Network  : {0}" -f $(if ([string]::IsNullOrWhiteSpace($DockerNetwork)) { "<auto>" } else { $DockerNetwork }))

& $runnerPath `
    -BaseUrl $BaseUrl `
    -Duration $Duration `
    -TargetRps $TargetRps `
    -PreAllocatedVus $PreAllocatedVus `
    -MaxVus $MaxVus `
    -DockerNetwork $DockerNetwork

if (-not (Test-Path $summaryPath)) {
    throw "Expected summary file not found: $summaryPath"
}

$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$archivedSummaryPath = Join-Path $evidenceDir "k6-sla-summary-$ts.json"
Copy-Item -Path $summaryPath -Destination $archivedSummaryPath -Force

$summary = Get-Content -Raw $archivedSummaryPath | ConvertFrom-Json

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

$evidenceReportPath = Join-Path $evidenceDir "k6-sla-evidence-$ts.md"
$report = @(
    "# k6 SLA Evidence"
    ""
    "- timestamp: $ts"
    "- baseUrl: $BaseUrl"
    "- duration: $Duration"
    "- targetRps: $TargetRps"
    "- archivedSummary: $archivedSummaryPath"
    ""
    "## Metrics"
    ""
    "- request rate: $([Math]::Round($rps, 2)) req/s"
    "- p95 latency: $([Math]::Round($p95, 2)) ms"
    "- failed rate: $([Math]::Round(($failedRate * 100.0), 4))%"
    "- checks pass rate: $([Math]::Round(($checkRate * 100.0), 4))%"
    ""
    "## Thresholds"
    ""
    "- achieved rate >= 95% of target: $(if ($thresholdRps) { 'PASS' } else { 'FAIL' })"
    "- http_req_failed < 1%: $(if ($thresholdFailRate) { 'PASS' } else { 'FAIL' })"
    "- http_req_duration p95 < 150ms: $(if ($thresholdP95) { 'PASS' } else { 'FAIL' })"
    "- checks pass rate > 99%: $(if ($thresholdChecks) { 'PASS' } else { 'FAIL' })"
) -join [Environment]::NewLine

Set-Content -Path $evidenceReportPath -Value $report -Encoding UTF8

Write-Host ""
Write-Host "Archived summary : $archivedSummaryPath"
Write-Host "Evidence report  : $evidenceReportPath"
