param(
    [string]$ClusterName = "edop",
    [string]$Namespace = "edop-dev",
    [int]$GatewayHostPort = 8080,
    [int]$ApiHostPort = 6550,
    [int]$HelmTimeoutMinutes = 30,
    [int]$ImageBuildRetries = 3,
    [int]$ImageBuildRetryDelaySec = 20,
    [switch]$ImportInfraImages
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$chartPath = Join-Path $root "infra/helm/edop"
$devValuesPath = Join-Path $root "infra/helm/values/dev.yaml"
$injectScriptPath = Join-Path $root "infra/k8s/inject-secrets.ps1"
$secretsDir = Join-Path $root ".secrets/dev"

function Test-K3dClusterExists {
    param([Parameter(Mandatory = $true)][string]$Name)

    try {
        $clusters = & k3d cluster list -o json 2>$null | ConvertFrom-Json
        foreach ($cluster in $clusters) {
            if ($cluster.name -eq $Name) {
                return $true
            }
        }
    }
    catch {
    }

    return $false
}

function Require-Command {
    param([Parameter(Mandatory = $true)][string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        $hint = switch ($Name) {
            "k3d"    { "Install with: choco install k3d -y" }
            "kubectl" { "Install with: choco install kubernetes-cli -y" }
            "helm"   { "Install with: choco install kubernetes-helm -y" }
            "docker" { "Install/start Docker Desktop first." }
            default  { "Install '$Name' and ensure it is in PATH." }
        }
        throw "Command '$Name' is not available in PATH. $hint"
    }
}

function Wait-HttpStatusCode {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$ExpectedStatusCode = 200,
        [int]$TimeoutSec = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $result = Invoke-WebRequest -UseBasicParsing -Uri $Url -Method Get -TimeoutSec 5
            if ($result.StatusCode -eq $ExpectedStatusCode) {
                return
            }
        }
        catch {
        }

        Start-Sleep -Seconds 3
    }

    throw "Timeout waiting for endpoint: $Url"
}

function Set-K3dAioLimit {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [int]$AioMaxNr = 1048576
    )

    $nodeContainers = @(
        "k3d-$Name-server-0",
        "k3d-$Name-agent-0"
    )

    foreach ($node in $nodeContainers) {
        & docker inspect $node *> $null
        if ($LASTEXITCODE -ne 0) {
            continue
        }

        Write-Host "Setting fs.aio-max-nr=$AioMaxNr on $node ..."
        & docker exec --user 0 $node sh -c "sysctl -w fs.aio-max-nr=$AioMaxNr" | Out-Host
    }
}

function Show-ClusterDiagnostics {
    param([Parameter(Mandatory = $true)][string]$Ns)

    Write-Host ""
    Write-Host "===== Diagnostics ($Ns) ====="
    try {
        & kubectl get pods -n $Ns -o wide | Out-Host
    }
    catch {
    }
    try {
        & kubectl get events -n $Ns --sort-by=.lastTimestamp | Select-Object -Last 40 | Out-Host
    }
    catch {
    }
    try {
        $notReadyPods = & kubectl get pods -n $Ns --no-headers 2>$null | Where-Object { $_ -match "0/1|0/2|CrashLoopBackOff|Error|ImagePullBackOff|ErrImagePull|ContainerCreating|Pending|Init:" }
        if ($notReadyPods) {
            Write-Host ""
            Write-Host "----- Recent logs from not-ready pods -----"
            foreach ($podLine in $notReadyPods) {
                $podName = ($podLine -split "\s+")[0]
                if (-not [string]::IsNullOrWhiteSpace($podName)) {
                    Write-Host ""
                    Write-Host "### $podName"
                    & kubectl logs -n $Ns $podName --all-containers --tail=120 | Out-Host
                }
            }
        }
    }
    catch {
    }
    Write-Host "===== End Diagnostics ====="
    Write-Host ""
}

function Recover-InfraCore {
    param([Parameter(Mandatory = $true)][string]$Ns)

    Write-Host "Attempting infra recovery for redpanda/schema-registry in namespace '$Ns'..."

    try {
        & kubectl rollout restart deployment/redpanda -n $Ns | Out-Host
    }
    catch {
        Write-Host "Skip redpanda restart: $_"
    }

    try {
        & kubectl rollout restart deployment/schema-registry -n $Ns | Out-Host
    }
    catch {
        Write-Host "Skip schema-registry restart: $_"
    }

    try {
        & kubectl wait -n $Ns --for=condition=Available deployment/redpanda --timeout=600s | Out-Host
    }
    catch {
        Write-Host "redpanda is not Available after recovery wait."
    }

    try {
        & kubectl wait -n $Ns --for=condition=Available deployment/schema-registry --timeout=600s | Out-Host
    }
    catch {
        Write-Host "schema-registry is not Available after recovery wait."
    }
}

function Ensure-NamespaceReady {
    param([Parameter(Mandatory = $true)][string]$Ns)

    $nsObj = $null
    $nsJson = & kubectl get namespace $Ns -o json --ignore-not-found 2>$null
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($nsJson)) {
        try {
            $nsObj = $nsJson | ConvertFrom-Json
        }
        catch {
            $nsObj = $null
        }
    }

    if ($null -ne $nsObj -and $nsObj.status.phase -eq "Terminating") {
        Write-Host "Namespace '$Ns' is Terminating. Clearing finalizers..."

        $tmpFile = Join-Path $env:TEMP ("{0}-finalize-{1}.json" -f $Ns, [guid]::NewGuid().ToString("N"))
        try {
            $nsObj.spec.finalizers = @()
            $nsObj | ConvertTo-Json -Depth 100 | Set-Content -Path $tmpFile -Encoding UTF8
            & kubectl replace --raw "/api/v1/namespaces/$Ns/finalize" -f $tmpFile | Out-Host
        }
        finally {
            Remove-Item -Path $tmpFile -Force -ErrorAction SilentlyContinue
        }

        $deadline = (Get-Date).AddSeconds(120)
        while ((Get-Date) -lt $deadline) {
            & kubectl get namespace $Ns *> $null
            if ($LASTEXITCODE -ne 0) {
                break
            }
            Start-Sleep -Seconds 2
        }

        & kubectl get namespace $Ns *> $null
        if ($LASTEXITCODE -eq 0) {
            throw "Namespace '$Ns' is still Terminating after finalizer cleanup."
        }
    }

    Write-Host "Ensuring namespace '$Ns' exists..."
    & kubectl create namespace $Ns --dry-run=client -o yaml | kubectl apply -f - | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to ensure namespace '$Ns'"
    }
}

function Ensure-DockerImage {
    param([Parameter(Mandatory = $true)][string]$Image)

    & docker image inspect $Image *> $null
    if ($LASTEXITCODE -eq 0) {
        return
    }

    Write-Host "Pulling helper image $Image ..."
    & docker pull $Image
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to pull helper image: $Image"
    }
}

function Build-ServiceImageWithRetry {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][string]$DockerfilePath,
        [Parameter(Mandatory = $true)][string]$ContextPath,
        [Parameter(Mandatory = $true)][string]$ImageTag,
        [int]$Retries = 3,
        [int]$DelaySec = 20
    )

    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        Write-Host "Building image $ImageTag (attempt $attempt/$Retries) ..."
        & docker build --provenance=false -f $DockerfilePath -t $ImageTag $ContextPath
        if ($LASTEXITCODE -eq 0) {
            return
        }

        if ($attempt -lt $Retries) {
            Write-Host "docker build failed for $ServiceName. Retrying in $DelaySec sec..."
            Start-Sleep -Seconds $DelaySec
        }
    }

    throw "docker build failed for $ServiceName after $Retries attempts"
}

Require-Command -Name "docker"
Require-Command -Name "k3d"
Require-Command -Name "kubectl"
Require-Command -Name "helm"

if (-not (Test-Path $chartPath)) {
    throw "Helm chart not found: $chartPath"
}
if (-not (Test-Path $devValuesPath)) {
    throw "Helm dev values not found: $devValuesPath"
}

Set-Location $root

$clusterExists = Test-K3dClusterExists -Name $ClusterName

if (-not $clusterExists) {
    $created = $false
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $apiPortForAttempt = $ApiHostPort + ($attempt - 1)
        Write-Host "Creating k3d cluster '$ClusterName' (attempt $attempt/3, api-port=$apiPortForAttempt)..."
        & k3d cluster create $ClusterName --servers 1 --agents 1 --wait --api-port "127.0.0.1:$apiPortForAttempt" -p "${GatewayHostPort}:30080@agent:0"
        if ($LASTEXITCODE -eq 0 -and (Test-K3dClusterExists -Name $ClusterName)) {
            $created = $true
            break
        }

        Start-Sleep -Seconds 3
        & k3d cluster delete $ClusterName *> $null
        Start-Sleep -Seconds 2
    }

    if (-not $created) {
        throw "Failed to create k3d cluster '$ClusterName' after 3 attempts."
    }
}
else {
    Write-Host "k3d cluster '$ClusterName' already exists."
}

Set-K3dAioLimit -Name $ClusterName

Write-Host "Ensuring Docker build helper images are available..."
$builderImages = @(
    "docker/dockerfile:1.7",
    "maven:3.9.9-eclipse-temurin-17",
    "eclipse-temurin:17-jre"
)
foreach ($builderImage in $builderImages) {
    Ensure-DockerImage -Image $builderImage
}

$serviceNames = @(
    "api-gateway",
    "auth-service",
    "user-service",
    "product-service",
    "inventory-service",
    "order-service",
    "notification-service"
)

$infraImages = @(
    "docker.redpanda.com/redpandadata/redpanda:v24.1.9",
    "confluentinc/cp-schema-registry:7.6.1",
    "postgres:15-alpine",
    "mongo:7",
    "redis:7-alpine",
    "jaegertracing/all-in-one:1.61.0"
)

if ($ImportInfraImages) {
    foreach ($infraImage in $infraImages) {
        Write-Host "Ensuring infra image $infraImage ..."
        & docker image inspect $infraImage *> $null
        if ($LASTEXITCODE -ne 0) {
            & docker pull $infraImage
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to pull infra image: $infraImage"
            }
        }
    }
}
else {
    Write-Host "Skipping infra image import (using cluster pulls for infra images)."
}

foreach ($serviceName in $serviceNames) {
    $dockerfilePath = Join-Path $root "services/$serviceName/Dockerfile"
    if (-not (Test-Path $dockerfilePath)) {
        throw "Missing Dockerfile: $dockerfilePath"
    }

    $imageTag = "edop/{0}:dev" -f $serviceName
    Build-ServiceImageWithRetry `
        -ServiceName $serviceName `
        -DockerfilePath $dockerfilePath `
        -ContextPath $root `
        -ImageTag $imageTag `
        -Retries $ImageBuildRetries `
        -DelaySec $ImageBuildRetryDelaySec
}

$images = @()
foreach ($serviceName in $serviceNames) {
    $images += ("edop/{0}:dev" -f $serviceName)
}
if ($ImportInfraImages) {
    foreach ($infraImage in $infraImages) {
        $images += $infraImage
    }
}

Write-Host "Importing images into k3d cluster '$ClusterName'..."
$previousErrorActionPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = "Continue"
    $importOutput = & k3d image import $images -c $ClusterName 2>&1
    $importExitCode = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $previousErrorActionPreference
}

$importOutput | Out-Host

$importText = ($importOutput | ForEach-Object { "$_" }) -join [Environment]::NewLine
$reportedImportSuccess = $importText -match "Successfully imported \d+ image\(s\) into \d+ cluster\(s\)"

if ($importExitCode -ne 0 -and -not $reportedImportSuccess) {
    throw "k3d image import failed"
}

if ($importExitCode -ne 0 -and $reportedImportSuccess) {
    Write-Host "k3d image import reported success despite stderr noise; continuing."
}

Ensure-NamespaceReady -Ns $Namespace

if (Test-Path $injectScriptPath) {
    & $injectScriptPath -Namespace $Namespace -SecretsDir $secretsDir
}

$helmSucceeded = $false
for ($helmAttempt = 1; $helmAttempt -le 2; $helmAttempt++) {
    Write-Host "Deploying Helm release (attempt $helmAttempt/2)..."
    & helm upgrade --install edop $chartPath -n $Namespace -f $devValuesPath --wait --timeout ("{0}m" -f $HelmTimeoutMinutes)
    if ($LASTEXITCODE -eq 0) {
        $helmSucceeded = $true
        break
    }

    Show-ClusterDiagnostics -Ns $Namespace

    if ($helmAttempt -lt 2) {
        Recover-InfraCore -Ns $Namespace
    }
}

if (-not $helmSucceeded) {
    throw "helm upgrade --install failed after 2 attempts"
}

Write-Host "Waiting for public gateway endpoint..."
Wait-HttpStatusCode -Url "http://localhost:$GatewayHostPort/swagger-ui.html" -ExpectedStatusCode 200 -TimeoutSec 240

Write-Host ""
Write-Host "k3d stack is ready."
Write-Host "Gateway public URL: http://localhost:$GatewayHostPort/"
Write-Host "Gateway swagger:    http://localhost:$GatewayHostPort/swagger-ui.html"
Write-Host "Gateway sample API: http://localhost:$GatewayHostPort/api/products"
Write-Host "Check pods:         kubectl get pods -n $Namespace"



