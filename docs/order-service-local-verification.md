# Order Service Local Verification

## 1. Prerequisites

- Local stack is running (`./dev-up.ps1` or `make dev-up`)
- Gateway is reachable on `http://localhost:8080`

## 2. Health check

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected:

```text
status
------
UP
```

## 3. Automated order lifecycle check (recommended)

PowerShell:

```powershell
.\infra\k8s\order-lifecycle-check.ps1
```

GNU Make:

```powershell
make order-smoke
```

What script verifies:

- Inventory item for selected `productId` exists and has enough stock.
- Order is created via gateway (`POST /api/orders`) and moves through:
  - `RESERVED -> PAID -> SHIPPED -> COMPLETED`
- Invalid transition `COMPLETED -> CANCELLED` is rejected with `409`.

## 4. Optional parameters

```powershell
.\infra\k8s\order-lifecycle-check.ps1 `
  -GatewayBaseUrl "http://localhost:8080" `
  -UserId 101 `
  -ProductId 2001 `
  -OrderQuantity 1 `
  -InventoryQuantity 100 `
  -RequestTimeoutSec 60 `
  -OrderCreateRetries 3 `
  -RetryDelaySec 5
```
