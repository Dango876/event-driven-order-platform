$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$composeFile = Join-Path $root "docker-compose.yaml"

function Get-ListeningProcessIds {
    param(
        [Parameter(Mandatory = $true)][int]$Port
    )

    $connections = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($connections.Count -eq 0) {
        return @()
    }

    return @($connections | Select-Object -ExpandProperty OwningProcess -Unique)
}

function Stop-DevProcessOnPort {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Port
    )

    $processIds = @(Get-ListeningProcessIds -Port $Port)
    if ($processIds.Count -eq 0) {
        Write-Host "[OK] $Name port $Port is free"
        return
    }

    foreach ($procId in $processIds) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($null -eq $proc) {
            Write-Host "[SKIP] $Name port $Port pid=$procId is already gone"
            continue
        }

        $procName = $proc.ProcessName.ToLowerInvariant()
        if ($procName -in @("java", "javaw", "mvn", "mvnw", "gradle", "gradlew")) {
            Stop-Process -Id $proc.Id -Force -ErrorAction Stop
            Write-Host "[OK] Stopped $Name host process pid=$($proc.Id) process=$($proc.ProcessName)"
        }
        else {
            Write-Host "[WARN] $Name port $Port is still owned by pid=$($proc.Id) process=$($proc.ProcessName). Not stopping automatically."
            if ($Port -eq 8080 -and $procName -in @("wslrelay", "com.docker.backend")) {
                Write-Host "[HINT] Port 8080 is likely held by the local k3d load balancer. Run .\\infra\\k8s\\k3d-down.ps1 before .\\dev-up.ps1 if you want the Docker Compose stack."
            }
        }
    }
}

$servicePorts = @(
    @{ Name = "Gateway"; Port = 8080 },
    @{ Name = "Auth Service"; Port = 8081 },
    @{ Name = "User Service"; Port = 8082 },
    @{ Name = "Product Service"; Port = 8083 },
    @{ Name = "Inventory Service"; Port = 8084 },
    @{ Name = "Order Service"; Port = 8086 },
    @{ Name = "Notification Service"; Port = 8087 },
    @{ Name = "Inventory gRPC"; Port = 9091 }
)

if (Test-Path $composeFile) {
    Write-Host "Stopping local stack..."
    docker compose -f $composeFile down | Out-Host

    if ($LASTEXITCODE -ne 0) {
        throw "docker compose down failed"
    }
}
else {
    Write-Host "docker-compose.yaml not found. Skipping docker compose down."
}

Write-Host "Cleaning host-run service ports..."
foreach ($item in $servicePorts) {
    Stop-DevProcessOnPort -Name $item.Name -Port $item.Port
}

Write-Host "Local stack is stopped."
