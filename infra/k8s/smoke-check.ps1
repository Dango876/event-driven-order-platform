param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-StatusCode {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$Expected = 200
    )

    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri $Url -Method Get -TimeoutSec 10
    }
    catch {
        throw "Cannot reach $Url. Ensure stack is running first (k3d-up or dev-up)."
    }
    if ($resp.StatusCode -ne $Expected) {
        throw "Unexpected status for $Url. Expected $Expected, got $($resp.StatusCode)."
    }
    Write-Host "[OK] $Url -> $($resp.StatusCode)"
}

function Assert-GatewayReady {
    param(
        [Parameter(Mandatory = $true)][string]$BaseUrl
    )

    try {
        $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec 10
        if ($health.status -eq "UP") {
            Write-Host "[OK] $BaseUrl/actuator/health -> UP"
            return
        }
    }
    catch {
    }

    Assert-StatusCode -Url "$BaseUrl/swagger-ui.html"
    Write-Host "[OK] Public gateway route is reachable (management health is not exposed on this base URL)."
}

Write-Host "Running smoke checks..."
Assert-GatewayReady -BaseUrl $BaseUrl
Assert-StatusCode -Url "$BaseUrl/swagger-ui.html"
Assert-StatusCode -Url "$BaseUrl/api-docs/auth"
Assert-StatusCode -Url "$BaseUrl/api-docs/user"
Assert-StatusCode -Url "$BaseUrl/api-docs/product"
Assert-StatusCode -Url "$BaseUrl/api-docs/inventory"
Assert-StatusCode -Url "$BaseUrl/api-docs/order"
Assert-StatusCode -Url "$BaseUrl/api-docs/notification"
Write-Host "Smoke checks passed."
