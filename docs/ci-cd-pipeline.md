# CI/CD Pipeline Baseline

This document describes the repository CI/CD baseline aligned with technical requirement 9.0.

## Workflows

- CI: `.github/workflows/ci.yml`
- Security: `.github/workflows/security.yml`
- CD: `.github/workflows/cd.yml`

## CI (pull requests and pushes)

CI pipeline includes:
- Checkstyle
- Maven tests (`mvn test`)
- API integration test coverage in `order-service` via:
  - `OrderLifecycleWebTestClientIT`
  - Testcontainers PostgreSQL
  - Spring `WebTestClient`
- JaCoCo reports for all application services:
  - `api-gateway`
  - `auth-service`
  - `user-service`
  - `product-service`
  - `inventory-service`
  - `order-service`
  - `notification-service`
- Aggregated platform instruction coverage gate (`>= 80%`) across all listed services

## CD (push to main/master or manual dispatch)

CD pipeline stages:
1. Build and push Docker images for all services to GHCR.
2. Helm deploy to `edop-dev` namespace (when `KUBE_CONFIG_DEV` is configured).
3. Helm deploy to `edop-prod` namespace after manual approval through GitHub `prod` environment (when `KUBE_CONFIG_PROD` is configured):
   - ingress TLS enabled,
   - autoscaling baseline enabled (`minReplicas=2`, `maxReplicas=4`),
   - PDB baseline enabled,
   - initial replicas set to `2` for all application deployments.
4. (Optional) Upsert TLS secret for gateway ingress from GitHub Secrets.

## Required repository setup

GitHub Secrets:
- `KUBE_CONFIG_DEV` - kubeconfig content for dev cluster
- `KUBE_CONFIG_PROD` - kubeconfig content for prod cluster
- `TLS_CERT_PEM_DEV` / `TLS_KEY_PEM_DEV` - optional dev ingress TLS cert/key
- `TLS_CERT_PEM_PROD` / `TLS_KEY_PEM_PROD` - optional prod ingress TLS cert/key

GitHub Environment:
- `prod` with required reviewers (manual approval gate)

Optional:
- `dev` environment for visibility and governance of dev deployments
- repository variable `PROD_GATEWAY_HOST` for prod ingress hostname (fallback: `edop.example.com`)

## Notes

- CD deploy jobs are skipped when corresponding kubeconfig secret is not present.
- Helm chart path used by CD: `infra/helm/edop`.
- Prod deploy enables ingress TLS in chart values and uses secret `edop-gateway-tls`.
- Prod deploy also enables HPA/PDB scaling baseline for availability and no-downtime rolling updates.
- Latest local aggregated JaCoCo result for service modules: `81.06%` instruction coverage (`2026-03-27`).
