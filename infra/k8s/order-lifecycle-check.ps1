param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [long]$UserId = 101,
    [long]$ProductId = 2001,
    [int]$OrderQuantity = 1,
    [int]$InventoryQuantity = 100,
    [int]$RequestTimeoutSec = 60,
    [int]$OrderCreateRetries = 3,
    [int]$RetryDelaySec = 5,
    [int]$ReservationWaitSec = 30,
    [string]$JwtSecret = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$DefaultDevJwtSecret = "VGhpc0lzRGV2T25seVN1cGVyU2VjcmV0S2V5VGhhdE11c3RCZUNoYW5nZWRJblByb2QxMjM0NQ=="
if ([string]::IsNullOrWhiteSpace($JwtSecret)) {
    if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
        $JwtSecret = $DefaultDevJwtSecret
    }
    else {
        $JwtSecret = $env:JWT_SECRET
    }
}

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
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -TimeoutSec $RequestTimeoutSec
    }

    $json = $Body | ConvertTo-Json -Depth 10
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -ContentType "application/json" -Body $json -TimeoutSec $RequestTimeoutSec
}

function ConvertTo-Base64Url {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-JwtToken {
    param(
        [Parameter(Mandatory = $true)][string]$Subject,
        [Parameter(Mandatory = $true)][string[]]$Roles
    )

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $headerJson = '{"alg":"HS256","typ":"JWT"}'
    $payloadJson = [ordered]@{
        sub = $Subject
        roles = $Roles
        iat = $now
        exp = $now + 3600
    } | ConvertTo-Json -Compress

    $headerPart = ConvertTo-Base64Url -Bytes ([Text.Encoding]::UTF8.GetBytes($headerJson))
    $payloadPart = ConvertTo-Base64Url -Bytes ([Text.Encoding]::UTF8.GetBytes($payloadJson))
    $unsignedToken = "$headerPart.$payloadPart"

    $secretBytes = [Convert]::FromBase64String($JwtSecret)
    $hmac = [System.Security.Cryptography.HMACSHA256]::new($secretBytes)
    try {
        $signatureBytes = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsignedToken))
    }
    finally {
        $hmac.Dispose()
    }

    $signaturePart = ConvertTo-Base64Url -Bytes $signatureBytes
    return "$unsignedToken.$signaturePart"
}

function Wait-ForOrderStatus {
    param(
        [Parameter(Mandatory = $true)][long]$OrderId,
        [Parameter(Mandatory = $true)][string[]]$ExpectedStatuses,
        [Parameter(Mandatory = $true)][hashtable]$Headers
    )

    $deadline = (Get-Date).AddSeconds($ReservationWaitSec)
    do {
        $current = Invoke-Json -Method "GET" -Url "$orderBaseUrl/$OrderId" -Headers $Headers
        if ($ExpectedStatuses -contains $current.status) {
            return $current
        }

        if ($current.status -eq "RESERVATION_FAILED") {
            throw "Order $OrderId reservation failed before reaching $($ExpectedStatuses -join ', ')."
        }

        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)

    throw "Timed out after $ReservationWaitSec sec waiting for order $OrderId to reach one of: $($ExpectedStatuses -join ', ')."
}

Write-Host "Running order lifecycle check..."
Write-Host "Gateway: $GatewayBaseUrl"

$adminHeaders = @{
    Authorization = "Bearer $(New-JwtToken -Subject 'order-check-admin@local' -Roles @('ROLE_ADMIN'))"
}
$userHeaders = @{
    Authorization = "Bearer $(New-JwtToken -Subject 'order-check-user@local' -Roles @('ROLE_USER'))"
}

$health = Invoke-Json -Method "GET" -Url "$GatewayBaseUrl/actuator/health"
if ($health.status -ne "UP") {
    throw "Gateway is not UP: $GatewayBaseUrl/actuator/health"
}
Write-Host "[OK] Gateway health is UP"

$inventoryBaseUrl = "$GatewayBaseUrl/api/inventory"

# Try to set available quantity first. If item does not exist yet, create it.
try {
    $inventoryItem = Invoke-Json -Method "PATCH" -Url "$inventoryBaseUrl/items/$ProductId/available?quantity=$InventoryQuantity" -Headers $adminHeaders
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
    } -Headers $adminHeaders
    Write-Host "[OK] Inventory item created for productId=$ProductId"
}

$inventoryItem = Invoke-Json -Method "GET" -Url "$inventoryBaseUrl/items/$ProductId" -Headers $adminHeaders
$free = [int]$inventoryItem.freeQuantity
if ($free -lt $OrderQuantity) {
    $reserved = [int]$inventoryItem.reservedQuantity
    $targetAvailable = [Math]::Max($InventoryQuantity, $reserved + $OrderQuantity + 10)
    $inventoryItem = Invoke-Json -Method "PATCH" -Url "$inventoryBaseUrl/items/$ProductId/available?quantity=$targetAvailable" -Headers $adminHeaders
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
        } -Headers $userHeaders
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

if ($order.status -ne "RESERVATION_PENDING" -and $order.status -ne "RESERVED") {
    throw "Unexpected initial status. Expected RESERVATION_PENDING or RESERVED, got $($order.status)."
}

$orderId = [long]$order.id
Write-Host "[OK] Order created: id=$orderId status=$($order.status)"

if ($order.status -ne "RESERVED") {
    $order = Wait-ForOrderStatus -OrderId $orderId -ExpectedStatuses @("RESERVED") -Headers $userHeaders
    Write-Host "[OK] Order moved to RESERVED"
}

foreach ($status in @("PAID", "SHIPPED", "COMPLETED")) {
    $updated = Invoke-Json -Method "PATCH" -Url "$orderBaseUrl/$orderId/status" -Body @{ status = $status } -Headers $adminHeaders
    if ($updated.status -ne $status) {
        throw "Failed to set status $status for order $orderId. Actual status: $($updated.status)"
    }
    Write-Host "[OK] Transitioned to $status"
}

$finalOrder = Invoke-Json -Method "GET" -Url "$orderBaseUrl/$orderId" -Headers $userHeaders
if ($finalOrder.status -ne "COMPLETED") {
    throw "Final status check failed. Expected COMPLETED, got $($finalOrder.status)"
}
Write-Host "[OK] Final status is COMPLETED"

try {
    $null = Invoke-Json -Method "PATCH" -Url "$orderBaseUrl/$orderId/status" -Body @{ status = "CANCELLED" } -Headers $adminHeaders
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
