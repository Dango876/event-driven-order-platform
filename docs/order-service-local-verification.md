# Order Service Local Verification

## 1. Prerequisites

- Local stack is running (`./dev-up.ps1` or `make dev-up`)
- Gateway is reachable on `http://localhost:8080`
- Seed users are available:
  - user: `rbac-user-2@example.com` / `Password123!`
  - admin: `rbac-admin-2@example.com` / `Password123!`

## 2. Health and login bootstrap

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
$userLogin = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body (@{ email = "rbac-user-2@example.com"; password = "Password123!" } | ConvertTo-Json -Compress)
$adminLogin = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body (@{ email = "rbac-admin-2@example.com"; password = "Password123!" } | ConvertTo-Json -Compress)

$USER_TOKEN = $userLogin.accessToken
$ADMIN_TOKEN = $adminLogin.accessToken

$userHeaders = @{ Authorization = "Bearer $USER_TOKEN" }
$adminHeaders = @{ Authorization = "Bearer $ADMIN_TOKEN" }
```

Expected:

- health endpoint returns `UP`
- both login calls return access tokens

## 3. Happy-path saga verification

Prepare stock and create an order:

```powershell
Invoke-RestMethod -Method PATCH -Uri "http://localhost:8080/api/inventory/items/2001/available?quantity=20" -Headers $adminHeaders

$orderBody = @{ userId = 101; productId = 2001; quantity = 1 } | ConvertTo-Json -Compress
$order = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/orders" -Headers $userHeaders -ContentType "application/json" -Body $orderBody
$order
```

Expected immediately after `POST /api/orders`:

- `status = RESERVATION_PENDING`

Poll until the asynchronous inventory reservation completes:

```powershell
$orderId = $order.id

1..15 | ForEach-Object {
  $current = Invoke-RestMethod -Method GET -Uri "http://localhost:8080/api/orders/$orderId" -Headers $userHeaders
  $current
  if ($current.status -ne "RESERVATION_PENDING") { break }
  Start-Sleep -Seconds 2
}
```

Expected result:

- order moves from `RESERVATION_PENDING` to `RESERVED`

Drive the manual status transitions:

```powershell
foreach ($status in @("PAID", "SHIPPED", "COMPLETED")) {
  $body = @{ status = $status } | ConvertTo-Json -Compress
  Invoke-RestMethod -Method PATCH -Uri "http://localhost:8080/api/orders/$orderId/status" -Headers $adminHeaders -ContentType "application/json" -Body $body
}

Invoke-RestMethod -Method GET -Uri "http://localhost:8080/api/orders/$orderId" -Headers $userHeaders
```

Expected final state:

- `status = COMPLETED`

Verify that an invalid transition is rejected:

```powershell
try {
  $body = @{ status = "CANCELLED" } | ConvertTo-Json -Compress
  Invoke-RestMethod -Method PATCH -Uri "http://localhost:8080/api/orders/$orderId/status" -Headers $adminHeaders -ContentType "application/json" -Body $body
} catch {
  $_.Exception.Response.StatusCode.value__
}
```

Expected result:

- HTTP `409`

## 4. Fail-path saga verification

Create or reset a zero-stock inventory item:

```powershell
try {
  Invoke-RestMethod -Method PATCH -Uri "http://localhost:8080/api/inventory/items/3999/available?quantity=0" -Headers $adminHeaders
} catch {
  $body = @{ productId = 3999; availableQuantity = 0 } | ConvertTo-Json -Compress
  Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/inventory/items" -Headers $adminHeaders -ContentType "application/json" -Body $body
}

$failBody = @{ userId = 101; productId = 3999; quantity = 1 } | ConvertTo-Json -Compress
$failedOrder = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/orders" -Headers $userHeaders -ContentType "application/json" -Body $failBody
$failedOrder
```

Expected immediately after `POST /api/orders`:

- `status = RESERVATION_PENDING`

Poll until the failure event is consumed:

```powershell
$failedOrderId = $failedOrder.id

1..15 | ForEach-Object {
  $current = Invoke-RestMethod -Method GET -Uri "http://localhost:8080/api/orders/$failedOrderId" -Headers $userHeaders
  $current
  if ($current.status -ne "RESERVATION_PENDING") { break }
  Start-Sleep -Seconds 2
}
```

Expected result:

- order moves from `RESERVATION_PENDING` to `RESERVATION_FAILED`

## 5. Optional Kafka and log evidence

```powershell
docker exec edop-redpanda rpk topic list
curl.exe http://localhost:8085/subjects
Get-Content .\.logs\inventory-service.app.log -Tail 100
Get-Content .\.logs\order-service.app.log -Tail 100
```

Expected evidence:

- Kafka topics include `inventory.reserve-failed`, `inventory.reserved`, `order.created`, `order.status-changed`
- Schema Registry subjects include matching `*-value` subjects
- `inventory-service` log shows publication of `inventory.reserved` and `inventory.reserve-failed`
- `order-service` log shows consumption of those inventory events and publication of `order.status-changed`
