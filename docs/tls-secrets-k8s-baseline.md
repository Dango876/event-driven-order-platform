# TLS 1.3 + Secrets (K8s Baseline)

## Scope

This baseline adds production-oriented security controls for Kubernetes deployment:

- TLS termination at ingress for `api-gateway`
- TLS protocol policy (TLS 1.3) via ingress annotations
- secret-driven runtime config for services (`envFrom` Kubernetes Secret)
- optional CD automation to upsert TLS secret from GitHub Secrets

## Helm chart changes

- Added optional ingress template:
  - `infra/helm/edop/templates/ingress.yaml`
- Added ingress values:
  - `ingress.enabled`
  - `ingress.host`
  - `ingress.tls.enabled`
  - `ingress.tls.secretName`
  - TLS/security annotations include `nginx.ingress.kubernetes.io/ssl-protocols: "TLSv1.3"`
- Extended deployment template secret support:
  - `envFromSecrets` now supports objects with `name` and `optional`

## Secrets baseline

Current values include optional secret references:

- `auth-service` -> secret `auth-service` (optional)
- `api-gateway` -> secret `api-gateway` (optional)

Local secret injection for k3d is available:

```powershell
.\infra\k8s\inject-secrets.ps1
```

It applies `.env` files from `.secrets/dev` as Kubernetes Secrets.

## CD integration

Workflow: `.github/workflows/cd.yml`

Added optional TLS secret upsert:

- dev (optional):
  - `TLS_CERT_PEM_DEV`
  - `TLS_KEY_PEM_DEV`
- prod (optional):
  - `TLS_CERT_PEM_PROD`
  - `TLS_KEY_PEM_PROD`

If provided, workflow creates/updates `edop-gateway-tls` in target namespace.

For prod deploy, Helm now enables ingress TLS and sets gateway service to `ClusterIP`.
Host is taken from repository variable `PROD_GATEWAY_HOST` (fallback: `edop.example.com`).

## Operational notes

- Ingress TLS annotations are nginx-oriented; use equivalent policy keys for other ingress controllers.
- If TLS secrets are not provided via CI, create `edop-gateway-tls` in cluster manually before prod deploy.
- This is a deploy baseline, not a certificate lifecycle solution. For full production, use cert-manager or platform PKI.
