# Redis Distributed Locks (Redlock Baseline)

## Goal

Add distributed lock protection for critical inventory write operations:

- create inventory item
- reserve stock
- release reservation
- update available quantity

This complements DB pessimistic locking and provides cross-instance coordination via Redis.

## Implementation

Service: `inventory-service`

- Lock manager:
  - `services/inventory-service/src/main/java/com/procurenhub/inventory/service/RedisDistributedLockService.java`
- Lock exception:
  - `services/inventory-service/src/main/java/com/procurenhub/inventory/service/DistributedLockException.java`
- Inventory operations wrapped with lock:
  - `services/inventory-service/src/main/java/com/procurenhub/inventory/service/InventoryService.java`
- HTTP mapping for lock failures:
  - `services/inventory-service/src/main/java/com/procurenhub/inventory/api/error/GlobalExceptionHandler.java` -> `503 Service Unavailable`

Lock behavior:

- key per product: `inventory:redlock:product:{productId}` (configurable)
- acquire by `SET NX PX` with retry loop and wait timeout
- release by Lua script (delete only if lock owner token matches)
- `fail-open` mode supported for controlled fallback to DB lock path

## Configuration

`services/inventory-service/src/main/resources/application.yml`:

- `REDIS_HOST`, `REDIS_PORT`, `REDIS_TIMEOUT`
- `INVENTORY_LOCK_ENABLED`
- `INVENTORY_LOCK_FAIL_OPEN`
- `INVENTORY_LOCK_WAIT_TIMEOUT_MS`
- `INVENTORY_LOCK_LEASE_TIMEOUT_MS`
- `INVENTORY_LOCK_RETRY_DELAY_MS`
- `INVENTORY_LOCK_KEY_PREFIX`

Helm values baseline includes Redis + lock env for `inventory-service`:

- `infra/helm/edop/values.yaml`

## Tests

Unit tests:

- `InventoryServiceDistributedLockTest`

Covered:

- lock is applied for reserve/create operations
- reserve path updates reserved/free quantities correctly under lock wrapper
- lock acquisition failure is propagated
