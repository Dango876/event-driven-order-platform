param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [long]$UserId = 101,
    [long]$ProductId = 2001,
    [int]$OrderQuantity = 1,
    [int]$InventoryQuantity = 100,
    [int]$RequestTimeoutSec = 60,
    [int]$OrderCreateRetries = 3,
    [int]$RetryDelaySec = 5
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-StatusCodeFromError {
    param([Parameter(Mandatory = $true)][System.Management.Automation.ErrorRecord]$ErrorRecord)

    try {
        return [int]$ErrorRecord.Exception.Response.StatusCode
    }
    catch {
        return $null
    }
}

function Get-ErrorResponseBody {
    param([Parameter(Mandatory = $true)][System.Management.Automation.ErrorRecord]$ErrorRecord)

    try {
        $response = $ErrorRecord.Exception.Response
        if ($null -eq $response) {
            return $null
        }

        $stream = $response.GetResponseStream()
        if ($null -eq $stream) {
            return $null
        }

        $reader = New-Object System.IO.StreamReader($stream)
        return $reader.ReadToEnd()
    }
    catch {
        return $null
    }
}

function Invoke-Json {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("GET", "POST", "PATCH")][string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        [object]$Body = $null
    )

    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Url -TimeoutSec $RequestTimeoutSec
    }

    $json = $Body | ConvertTo-Json -Depth 10
    return Invoke-RestMethod -Method $Method -Uri $Url -ContentType "application/json" -Body $json -TimeoutSec $RequestTimeoutSec
}

Write-Host "Running order lifecycle check..."
Write-Host "Gateway: $GatewayBaseUrl"

$health = Invoke-Json -Method "GET" -Url "$GatewayBaseUrl/actuator/health"
if ($health.status -ne "UP") {
    throw "Gateway is not UP: $GatewayBaseUrl/actuator/health"
}
Write-Host "[OK] Gateway health is UP"

$inventoryBaseUrl = "$GatewayBaseUrl/api/inventory"

# Try to set available quantity first. If item does not exist yet, create it.
try {
    $inventoryItem = Invoke-Json -Method "PATCH" -Url "$inventoryBaseUrl/items/$ProductId/available?quantity=$InventoryQuantity"
    Write-Host "[OK] Inventory item updated for productId=$ProductId"
}
catch {
    $statusCode = Get-StatusCodeFromError -ErrorRecord $_
    if ($statusCode -ne 400 -and $statusCode -ne 404) {
        throw
    }

    $inventoryItem = Invoke-Json -Method "POST" -Url "$inventoryBaseUrl/items" -Body @{
        productId = $ProductId
        availableQuantity = $InventoryQuantity
    }
    Write-Host "[OK] Inventory item created for productId=$ProductId"
}

$inventoryItem = Invoke-Json -Method "GET" -Url "$inventoryBaseUrl/items/$ProductId"
$free = [int]$inventoryItem.freeQuantity
if ($free -lt $OrderQuantity) {
    $reserved = [int]$inventoryItem.reservedQuantity
    $targetAvailable = [Math]::Max($InventoryQuantity, $reserved + $OrderQuantity + 10)
    $inventoryItem = Invoke-Json -Method "PATCH" -Url "$inventoryBaseUrl/items/$ProductId/available?quantity=$targetAvailable"
    Write-Host "[OK] Inventory adjusted to availableQuantity=$targetAvailable"
}

$orderBaseUrl = "$GatewayBaseUrl/api/orders"
$order = $null
for ($attempt = 1; $attempt -le $OrderCreateRetries; $attempt++) {
    try {
        $order = Invoke-Json -Method "POST" -Url $orderBaseUrl -Body @{
            userId = $UserId
            productId = $ProductId
            quantity = $OrderQuantity
        }
        break
    }
    catch {
        $statusCode = Get-StatusCodeFromError -ErrorRecord $_
        $isTransient = ($null -eq $statusCode) -or ($statusCode -ge 500) -or ($statusCode -eq 429)
        if (-not $isTransient -or $attempt -eq $OrderCreateRetries) {
            $body = Get-ErrorResponseBody -ErrorRecord $_
            if (-not [string]::IsNullOrWhiteSpace($body)) {
                Write-Host "[ERROR] Order create response: $body"
            }
            throw
        }

        Write-Host "[WARN] Order create failed (attempt $attempt/$OrderCreateRetries, status=$statusCode). Retrying in $RetryDelaySec sec..."
        Start-Sleep -Seconds $RetryDelaySec
    }
}

if ($null -eq $order.id) {
    throw "Order creation failed: response does not contain order id."
}

if ($order.status -ne "RESERVED") {
    throw "Unexpected initial status. Expected RESERVED, got $($order.status)."
}

$orderId = [long]$order.id
Write-Host "[OK] Order created: id=$orderId status=RESERVED"

foreach ($status in @("PAID", "SHIPPED", "COMPLETED")) {
    $updated = Invoke-Json -Method "PATCH" -Url "$orderBaseUrl/$orderId/status" -Body @{ status = $status }
    if ($updated.status -ne $status) {
        throw "Failed to set status $status for order $orderId. Actual status: $($updated.status)"
    }
    Write-Host "[OK] Transitioned to $status"
}

$finalOrder = Invoke-Json -Method "GET" -Url "$orderBaseUrl/$orderId"
if ($finalOrder.status -ne "COMPLETED") {
    throw "Final status check failed. Expected COMPLETED, got $($finalOrder.status)"
}
Write-Host "[OK] Final status is COMPLETED"

try {
    $null = Invoke-Json -Method "PATCH" -Url "$orderBaseUrl/$orderId/status" -Body @{ status = "CANCELLED" }
    throw "Expected 409 on COMPLETED -> CANCELLED, but request succeeded."
}
catch {
    $statusCode = Get-StatusCodeFromError -ErrorRecord $_
    if ($statusCode -ne 409) {
        throw "Expected HTTP 409 on invalid transition, got $statusCode"
    }
    Write-Host "[OK] Invalid transition COMPLETED -> CANCELLED rejected with 409"
}

Write-Host ""
Write-Host "Order lifecycle check passed."
Write-Host "Verified orderId: $orderId"
