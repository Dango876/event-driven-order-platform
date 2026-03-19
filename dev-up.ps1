$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$composeFile = Join-Path $root "docker-compose.yaml"
$mvnw = Join-Path $root "mvnw.cmd"
$runDir = Join-Path $root ".run"
$logDir = Join-Path $root ".logs"
$pidFile = Join-Path $runDir "dev-processes.json"

if (-not (Test-Path $composeFile)) {
    throw "docker-compose.yaml not found: $composeFile"
}

if (-not (Test-Path $mvnw)) {
    throw "mvnw.cmd not found: $mvnw"
}

New-Item -ItemType Directory -Path $runDir -Force | Out-Null
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

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
        [int]$TimeoutSec = 90
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
        [int]$TimeoutSec = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-RestMethod -Uri $Url -TimeoutSec 3
            if ($null -ne $resp.status -and $resp.status -eq "UP") {
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
            $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($null -ne $resp -and $resp.StatusCode -eq $ExpectedStatusCode) {
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

function Get-ProcessNameById {
    param(
        [Parameter(Mandatory = $true)][int]$ProcessId
    )

    try {
        return (Get-Process -Id $ProcessId -ErrorAction Stop).ProcessName
    }
    catch {
        return "unknown"
    }
}

$infraPorts = @(
    @{ Name = "Redpanda Kafka"; Port = 9092 },
    @{ Name = "Schema Registry"; Port = 8085 },
    @{ Name = "Auth Postgres"; Port = 5433 },
    @{ Name = "User Postgres"; Port = 5434 },
    @{ Name = "Inventory Postgres"; Port = 5435 },
    @{ Name = "Order Postgres"; Port = 5436 },
    @{ Name = "MongoDB"; Port = 27018 },
    @{ Name = "Redis"; Port = 6379 },
    @{ Name = "Prometheus"; Port = 9090 },
    @{ Name = "Alertmanager"; Port = 9093 },
    @{ Name = "Alert Webhook Sink"; Port = 8088 },
    @{ Name = "Jaeger UI"; Port = 16686 },
    @{ Name = "Jaeger OTLP"; Port = 4318 },
    @{ Name = "Grafana"; Port = 3000 },
    @{ Name = "Loki"; Port = 3100 }
)

Write-Host "Starting infra containers..."
docker compose -f $composeFile up -d | Out-Host
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "docker compose failed. Port occupancy snapshot:"
    foreach ($p in $infraPorts) {
        $ownerProcessId = Get-ListeningProcessId -Port $p.Port
        if ($null -eq $ownerProcessId) {
            Write-Host ("  {0}: free" -f $p.Port)
        }
        else {
            $procName = "unknown"
            try {
                $procName = (Get-Process -Id $ownerProcessId -ErrorAction Stop).ProcessName
            }
            catch {
            }
            Write-Host ("  {0}: pid={1} ({2})" -f $p.Port, $ownerProcessId, $procName)
        }
    }
    throw "docker compose up failed"
}

foreach ($p in $infraPorts) {
    Wait-TcpPort -Name $p.Name -Port $p.Port -TimeoutSec 120
}

Wait-HttpStatus -Name "Prometheus readiness" -Url "http://localhost:9090/-/ready" -ExpectedStatusCode 200 -TimeoutSec 120
Wait-HttpStatus -Name "Alertmanager readiness" -Url "http://localhost:9093/-/ready" -ExpectedStatusCode 200 -TimeoutSec 120
Wait-HttpStatus -Name "Jaeger UI" -Url "http://localhost:16686" -ExpectedStatusCode 200 -TimeoutSec 120
Wait-HttpStatus -Name "Grafana health" -Url "http://localhost:3000/api/health" -ExpectedStatusCode 200 -TimeoutSec 120
Wait-HttpStatus -Name "Loki readiness" -Url "http://localhost:3100/ready" -ExpectedStatusCode 200 -TimeoutSec 120

$services = @(
    @{ Name = "auth-service";         Port = 8081; Pom = "services/auth-service/pom.xml";         Health = "http://localhost:8081/actuator/health" },
    @{ Name = "user-service";         Port = 8082; Pom = "services/user-service/pom.xml";         Health = "http://localhost:8082/actuator/health" },
    @{ Name = "product-service";      Port = 8083; Pom = "services/product-service/pom.xml";      Health = "http://localhost:8083/actuator/health" },
    @{ Name = "inventory-service";    Port = 8084; Pom = "services/inventory-service/pom.xml";    Health = "http://localhost:8084/actuator/health" },
    @{ Name = "order-service";        Port = 8086; Pom = "services/order-service/pom.xml";        Health = "http://localhost:8086/actuator/health" },
    @{ Name = "notification-service"; Port = 8087; Pom = "services/notification-service/pom.xml"; Health = "http://localhost:8087/actuator/health" },
    @{ Name = "api-gateway";          Port = 8080; Pom = "services/api-gateway/pom.xml";          Health = "http://localhost:8080/actuator/health" }
)

$started = @()

foreach ($svc in $services) {
    $existingPid = Get-ListeningProcessId -Port $svc.Port
    if ($null -ne $existingPid) {
        $existingProcName = Get-ProcessNameById -ProcessId $existingPid
        $isJavaLike = $existingProcName -like "java*"
        if (-not $isJavaLike) {
            throw "Port $($svc.Port) for $($svc.Name) is occupied by pid=$existingPid ($existingProcName). Stop conflicting process (for k3d: .\\infra\\k8s\\k3d-down.ps1) and run .\\dev-up.ps1 again."
        }

        Write-Host "[SKIP] $($svc.Name) already listens on $($svc.Port) (pid=$existingPid)"
        $started += [PSCustomObject]@{
            name            = $svc.Name
            port            = $svc.Port
            processId       = $existingPid
            startedByScript = $false
            stdoutLog       = $null
            stderrLog       = $null
        }
        Wait-HealthEndpoint -Name $svc.Name -Url $svc.Health -TimeoutSec 120
        continue
    }

    $outLog = Join-Path $logDir "$($svc.Name).out.log"
    $errLog = Join-Path $logDir "$($svc.Name).err.log"
    $args = @("-U", "-f", $svc.Pom, "spring-boot:run")

    Write-Host "[START] $($svc.Name) on port $($svc.Port)"
    $proc = Start-Process -FilePath $mvnw -ArgumentList $args -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog

    $started += [PSCustomObject]@{
        name            = $svc.Name
        port            = $svc.Port
        processId       = $proc.Id
        startedByScript = $true
        stdoutLog       = $outLog
        stderrLog       = $errLog
    }

    Wait-HealthEndpoint -Name $svc.Name -Url $svc.Health -TimeoutSec 240
}

$started | ConvertTo-Json -Depth 4 | Set-Content -Path $pidFile -Encoding UTF8

Write-Host ""
Write-Host "Local stack is ready."
Write-Host "Gateway health:  http://localhost:8080/actuator/health"
Write-Host "Gateway swagger: http://localhost:8080/swagger-ui.html"
Write-Host "Stop everything: .\\dev-down.ps1"
