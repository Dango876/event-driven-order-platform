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
- JaCoCo report and coverage gate (`>= 80%`)

## CD (push to main/master or manual dispatch)

CD pipeline stages:
1. Build and push Docker images for all services to GHCR.
2. Helm deploy to `edop-dev` namespace (when `KUBE_CONFIG_DEV` is configured).
3. Helm deploy to `edop-prod` namespace after manual approval through GitHub `prod` environment (when `KUBE_CONFIG_PROD` is configured).

## Required repository setup

GitHub Secrets:
- `KUBE_CONFIG_DEV` - kubeconfig content for dev cluster
- `KUBE_CONFIG_PROD` - kubeconfig content for prod cluster

GitHub Environment:
- `prod` with required reviewers (manual approval gate)

Optional:
- `dev` environment for visibility and governance of dev deployments

## Notes

- CD deploy jobs are skipped when corresponding kubeconfig secret is not present.
- Helm chart path used by CD: `infra/helm/edop`.
