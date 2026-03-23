$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$composeFile = Join-Path $root "docker-compose.yaml"

if (-not (Test-Path $composeFile)) {
    throw "docker-compose.yaml not found: $composeFile"
}

function Test-TcpPort {
    param(
        [Parameter(Mandatory = $true)][string]$TargetHost,
        [Parameter(Mandatory = $true)][int]$Port
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($TargetHost, $Port)
        if (-not $task.Wait(500)) {
            return $false
        }
        return $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Wait-TcpPort {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Port,
        [int]$TimeoutSec = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -TargetHost "127.0.0.1" -Port $Port) {
            Write-Host "[OK] $Name port $Port is ready"
            return
        }
        Start-Sleep -Seconds 2
    }

    throw "$Name port $Port did not become ready in ${TimeoutSec}s"
}

function Wait-HealthEndpoint {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$TimeoutSec = 240
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = & curl.exe -fsS $Url 2>$null
            if ($LASTEXITCODE -eq 0 -and $resp -match '"status"\s*:\s*"UP"') {
                Write-Host "[OK] $Name health is UP"
                return
            }
        }
        catch {
        }

        Start-Sleep -Seconds 2
    }

    throw "$Name health endpoint is not UP: $Url"
}

function Wait-HttpStatus {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$ExpectedStatusCode = 200,
        [int]$TimeoutSec = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $statusCode = & curl.exe -fsS -o NUL -w "%{http_code}" $Url 2>$null
            if ($LASTEXITCODE -eq 0 -and $statusCode -eq [string]$ExpectedStatusCode) {
                Write-Host "[OK] $Name is ready"
                return
            }
        }
        catch {
        }

        Start-Sleep -Seconds 2
    }

    throw "$Name is not ready: $Url"
}

function Get-ListeningProcessId {
    param(
        [Parameter(Mandatory = $true)][int]$Port
    )

    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $conn) {
        return $null
    }
    return [int]$conn.OwningProcess
}

$stackPorts = @(
    @{ Name = "Gateway"; Port = 8080 },
    @{ Name = "Auth Service"; Port = 8081 },
    @{ Name = "User Service"; Port = 8082 },
    @{ Name = "Product Service"; Port = 8083 },
    @{ Name = "Inventory Service"; Port = 8084 },
    @{ Name = "Schema Registry"; Port = 8085 },
    @{ Name = "Order Service"; Port = 8086 },
    @{ Name = "Notification Service"; Port = 8087 },
    @{ Name = "Alert Webhook Sink"; Port = 8088 },
    @{ Name = "Prometheus"; Port = 9090 },
    @{ Name = "Inventory gRPC"; Port = 9091 },
    @{ Name = "Alertmanager"; Port = 9093 },
    @{ Name = "Grafana"; Port = 3000 },
    @{ Name = "Loki"; Port = 3100 },
    @{ Name = "Jaeger UI"; Port = 16686 },
    @{ Name = "MongoDB"; Port = 27018 },
    @{ Name = "Auth Postgres"; Port = 5433 },
    @{ Name = "User Postgres"; Port = 5434 },
    @{ Name = "Inventory Postgres"; Port = 5435 },
    @{ Name = "Order Postgres"; Port = 5436 },
    @{ Name = "Redis"; Port = 6379 },
    @{ Name = "Redpanda Kafka"; Port = 9092 },
    @{ Name = "Redpanda HTTP Proxy"; Port = 18082 },
    @{ Name = "Jaeger OTLP"; Port = 4318 }
)

Write-Host "Starting full local stack with Docker Compose..."
docker compose -f $composeFile up -d --build | Out-Host
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "docker compose failed. Port occupancy snapshot:"
    foreach ($p in $stackPorts) {
        $ownerProcessId = Get-ListeningProcessId -Port $p.Port
        if ($null -eq $ownerProcessId) {
            Write-Host ("  {0}: free" -f $p.Port)
        }
        else {
            Write-Host ("  {0}: pid={1}" -f $p.Port, $ownerProcessId)
        }
    }
    throw "docker compose up failed"
}

foreach ($p in $stackPorts) {
    Wait-TcpPort -Name $p.Name -Port $p.Port
}

Wait-HttpStatus -Name "Prometheus readiness" -Url "http://localhost:9090/-/ready"
Wait-HttpStatus -Name "Alertmanager readiness" -Url "http://localhost:9093/-/ready"
Wait-HttpStatus -Name "Jaeger UI" -Url "http://localhost:16686"
Wait-HttpStatus -Name "Grafana health" -Url "http://localhost:3000/api/health"
Wait-HttpStatus -Name "Loki readiness" -Url "http://localhost:3100/ready"

Wait-HealthEndpoint -Name "auth-service" -Url "http://localhost:8081/actuator/health"
Wait-HealthEndpoint -Name "user-service" -Url "http://localhost:8082/actuator/health"
Wait-HealthEndpoint -Name "product-service" -Url "http://localhost:8083/actuator/health"
Wait-HealthEndpoint -Name "inventory-service" -Url "http://localhost:8084/actuator/health"
Wait-HealthEndpoint -Name "order-service" -Url "http://localhost:8086/actuator/health"
Wait-HealthEndpoint -Name "notification-service" -Url "http://localhost:8087/actuator/health"
Wait-HealthEndpoint -Name "api-gateway" -Url "http://localhost:8080/actuator/health"

Write-Host ""
Write-Host "Local stack is ready."
Write-Host "Gateway health:  http://localhost:8080/actuator/health"
Write-Host "Gateway swagger: http://localhost:8080/swagger-ui.html"
Write-Host "Stop everything: .\\dev-down.ps1"
