$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$composeFile = Join-Path $root "docker-compose.yaml"
$pidFile = Join-Path (Join-Path $root ".run") "dev-processes.json"
$servicePorts = @(
    @{ Name = "api-gateway"; Port = 8080 },
    @{ Name = "auth-service"; Port = 8081 },
    @{ Name = "user-service"; Port = 8082 },
    @{ Name = "product-service"; Port = 8083 },
    @{ Name = "inventory-service"; Port = 8084 },
    @{ Name = "order-service"; Port = 8086 },
    @{ Name = "notification-service"; Port = 8087 }
)

function Stop-StaleServiceProcessByPort {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Port
    )

    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $conn) {
        return
    }

    $processIdToStop = [int]$conn.OwningProcess
    try {
        $proc = Get-Process -Id $processIdToStop -ErrorAction Stop
        # Stop Java/Maven listeners on known service ports.
        $isJavaLike = ($proc.ProcessName -like "java*") -or ($proc.ProcessName -like "mvn*")
        if ($isJavaLike) {
            Stop-Process -Id $processIdToStop -Force
            Write-Host "[STOPPED] stale $Name pid=$processIdToStop on port $Port"
        }
    }
    catch {
    }
}

if (Test-Path $pidFile) {
    Write-Host "Stopping services started by dev-up..."
    $raw = Get-Content $pidFile -Raw
    if (-not [string]::IsNullOrWhiteSpace($raw)) {
        $records = @((ConvertFrom-Json $raw))
        foreach ($rec in $records) {
            if ($rec.startedByScript -eq $true) {
                $processIdToStop = [int]$rec.processId
                try {
                    $proc = Get-Process -Id $processIdToStop -ErrorAction Stop
                    Stop-Process -Id $proc.Id -Force
                    Write-Host "[STOPPED] $($rec.name) pid=$processIdToStop"
                }
                catch {
                    Write-Host "[SKIP] $($rec.name) pid=$processIdToStop is already stopped"
                }
            }
        }
    }
    Remove-Item $pidFile -Force
}
else {
    Write-Host "No .run/dev-processes.json found. Skipping Java process stop."
}

foreach ($svc in $servicePorts) {
    Stop-StaleServiceProcessByPort -Name $svc.Name -Port $svc.Port
}

if (Test-Path $composeFile) {
    Write-Host "Stopping infra containers..."
    docker compose -f $composeFile down | Out-Host
}
else {
    Write-Host "docker-compose.yaml not found. Skipping docker compose down."
}

Write-Host "Local stack is stopped."
