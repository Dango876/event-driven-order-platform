# Swagger/OpenAPI Endpoints

## Unified entry point (API Gateway)

- Gateway Swagger UI: `http://localhost:8080/swagger-ui.html`
- Gateway health: `http://localhost:8080/actuator/health`

Gateway Swagger/OpenAPI routes exposed through the API gateway:

### JSON OpenAPI routes

- `auth-service`: `http://localhost:8080/api-docs/auth`
- `user-service`: `http://localhost:8080/api-docs/user`
- `product-service`: `http://localhost:8080/api-docs/product`
- `inventory-service`: `http://localhost:8080/api-docs/inventory`
- `order-service`: `http://localhost:8080/api-docs/order`
- `notification-service`: `http://localhost:8080/api-docs/notification`

### YAML OpenAPI routes

- `auth-service`: `http://localhost:8080/api-docs/auth.yaml`
- `user-service`: `http://localhost:8080/api-docs/user.yaml`
- `product-service`: `http://localhost:8080/api-docs/product.yaml`
- `inventory-service`: `http://localhost:8080/api-docs/inventory.yaml`
- `order-service`: `http://localhost:8080/api-docs/order.yaml`
- `notification-service`: `http://localhost:8080/api-docs/notification.yaml`

## Direct service URLs

- `auth-service` (`8081`)
  - UI: `http://localhost:8081/swagger-ui/index.html`
  - OpenAPI JSON: `http://localhost:8081/v3/api-docs`
  - OpenAPI YAML: `http://localhost:8081/v3/api-docs.yaml`

- `user-service` (`8082`)
  - UI: `http://localhost:8082/swagger-ui/index.html`
  - OpenAPI JSON: `http://localhost:8082/v3/api-docs`
  - OpenAPI YAML: `http://localhost:8082/v3/api-docs.yaml`

- `product-service` (`8083`)
  - UI: `http://localhost:8083/swagger-ui/index.html`
  - OpenAPI JSON: `http://localhost:8083/v3/api-docs`
  - OpenAPI YAML: `http://localhost:8083/v3/api-docs.yaml`

- `inventory-service` (`8084`)
  - UI: `http://localhost:8084/swagger-ui/index.html`
  - OpenAPI JSON: `http://localhost:8084/v3/api-docs`
  - OpenAPI YAML: `http://localhost:8084/v3/api-docs.yaml`

- `order-service` (`8086`)
  - UI: `http://localhost:8086/swagger-ui/index.html`
  - OpenAPI JSON: `http://localhost:8086/v3/api-docs`
  - OpenAPI YAML: `http://localhost:8086/v3/api-docs.yaml`

- `notification-service` (`8087`)
  - UI: `http://localhost:8087/swagger-ui/index.html`
  - OpenAPI JSON: `http://localhost:8087/v3/api-docs`
  - OpenAPI YAML: `http://localhost:8087/v3/api-docs.yaml`

## Quick validation (PowerShell)

```powershell
$urls = @(
  "http://localhost:8080/actuator/health",
  "http://localhost:8080/api-docs/auth",
  "http://localhost:8080/api-docs/user",
  "http://localhost:8080/api-docs/product",
  "http://localhost:8080/api-docs/inventory",
  "http://localhost:8080/api-docs/order",
  "http://localhost:8080/api-docs/notification",
  "http://localhost:8080/api-docs/auth.yaml",
  "http://localhost:8080/api-docs/user.yaml",
  "http://localhost:8080/api-docs/product.yaml",
  "http://localhost:8080/api-docs/inventory.yaml",
  "http://localhost:8080/api-docs/order.yaml",
  "http://localhost:8080/api-docs/notification.yaml"
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
