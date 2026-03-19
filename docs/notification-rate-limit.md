# Notification Rate Limit (Redis Leaky Bucket)

## Goal

Limit notification fan-out per user in `notification-service` using Redis leaky bucket.

## How it works

- Bucket key per user: `notification:bucket:user:<userId>`
- On each event:
  - bucket leaks by `leak-per-second` (integer rate)
  - if bucket level reaches `capacity`, notification is skipped
- `order.created` contains `userId`, so mapping is stored:
  - `notification:order-user:<orderId> -> <userId>`
- `order.status-changed` resolves user by `orderId` mapping.
- If mapping is unavailable, fallback bucket is used:
  - `notification:bucket:order:<orderId>`

## Config

Environment variables:

- `REDIS_HOST` (default `localhost`)
- `REDIS_PORT` (default `6379`)
- `NOTIFICATION_RATE_LIMIT_ENABLED` (default `true`)
- `NOTIFICATION_RATE_LIMIT_CAPACITY` (default `20`)
- `NOTIFICATION_RATE_LIMIT_LEAK_PER_SECOND` (default `5`)
- `NOTIFICATION_RATE_LIMIT_BUCKET_TTL_SECONDS` (default `3600`)
- `NOTIFICATION_RATE_LIMIT_ORDER_USER_TTL_SECONDS` (default `604800`)

## Local check

1. Start stack:

```powershell
.\dev-up.ps1
```

2. Ensure Redis port is reachable:

```powershell
(Test-NetConnection -ComputerName localhost -Port 6379).TcpTestSucceeded
```

3. Create traffic (order flow script or manual orders) and inspect notification logs:

```powershell
Get-Content .\.logs\notification-service.out.log -Tail 200
```

4. Expected behavior:
- regular notification logs are present;
- when limit is exceeded, warning appears:
  - `Notification rate limit exceeded ...`
