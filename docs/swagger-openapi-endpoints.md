# Swagger/OpenAPI Endpoints

## Unified entry point (API Gateway)

- Gateway Swagger UI: `http://localhost:8080/swagger-ui.html`
- Gateway health: `http://localhost:8080/actuator/health`

Gateway Swagger aggregates docs from:
- auth-service
- user-service
- product-service
- inventory-service
- order-service
- notification-service

## Direct service URLs

- `auth-service` (`8081`)
  - UI: `http://localhost:8081/swagger-ui/index.html`
  - OpenAPI: `http://localhost:8081/v3/api-docs`
- `user-service` (`8082`)
  - UI: `http://localhost:8082/swagger-ui/index.html`
  - OpenAPI: `http://localhost:8082/v3/api-docs`
- `product-service` (`8083`)
  - UI: `http://localhost:8083/swagger-ui/index.html`
  - OpenAPI: `http://localhost:8083/v3/api-docs`
- `inventory-service` (`8084`)
  - UI: `http://localhost:8084/swagger-ui/index.html`
  - OpenAPI: `http://localhost:8084/v3/api-docs`
- `order-service` (`8086`)
  - UI: `http://localhost:8086/swagger-ui/index.html`
  - OpenAPI: `http://localhost:8086/v3/api-docs`
- `notification-service` (`8087`)
  - UI: `http://localhost:8087/swagger-ui/index.html`
  - OpenAPI: `http://localhost:8087/v3/api-docs`

## Quick validation (PowerShell)

```powershell
$urls = @(
  "http://localhost:8080/actuator/health",
  "http://localhost:8081/v3/api-docs",
  "http://localhost:8082/v3/api-docs",
  "http://localhost:8083/v3/api-docs",
  "http://localhost:8084/v3/api-docs",
  "http://localhost:8086/v3/api-docs",
  "http://localhost:8087/v3/api-docs"
)

foreach ($u in $urls) {
  try {
    $res = Invoke-WebRequest -UseBasicParsing $u
    "{0} -> {1}" -f $u, $res.StatusCode
  } catch {
    "{0} -> FAILED ({1})" -f $u, $_.Exception.Message
  }
}
```
