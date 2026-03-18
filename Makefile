.PHONY: dev-up dev-down k3d-up k3d-down inject-secrets k3d-smoke order-smoke

dev-up:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./dev-up.ps1

dev-down:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./dev-down.ps1

k3d-up:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./infra/k8s/k3d-up.ps1

k3d-down:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./infra/k8s/k3d-down.ps1

inject-secrets:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./infra/k8s/inject-secrets.ps1

k3d-smoke:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./infra/k8s/smoke-check.ps1

order-smoke:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./infra/k8s/order-lifecycle-check.ps1
