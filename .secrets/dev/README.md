# Dev Secrets For k3d

Put one or more `*.env` files in this folder.

Each file becomes one Kubernetes Secret:
- filename without extension = secret name
- each `KEY=VALUE` line = secret entry

Example:

File: `.secrets/dev/auth-service.env`

```env
JWT_SECRET=replace-with-dev-secret
OAUTH_GOOGLE_CLIENT_ID=replace
OAUTH_GOOGLE_CLIENT_SECRET=replace
```

Optional:

File: `.secrets/dev/api-gateway.env`

```env
SOME_GATEWAY_SECRET=replace-with-dev-secret
```

Apply them with:

```powershell
make inject-secrets
```

Or:

```powershell
.\infra\k8s\inject-secrets.ps1
```
