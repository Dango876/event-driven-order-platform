param(
    [string]$Namespace = "edop-dev",
    [string]$SecretsDir = ".secrets/dev"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "Command 'kubectl' is not available in PATH."
}

$resolvedSecretsDir = $SecretsDir
if (-not (Test-Path $resolvedSecretsDir)) {
    Write-Host "Secrets directory '$resolvedSecretsDir' not found. Skipping."
    exit 0
}

& kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f - | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Failed to ensure namespace '$Namespace'"
}

$secretFiles = Get-ChildItem -Path $resolvedSecretsDir -File -Filter "*.env" -ErrorAction SilentlyContinue
if ($null -eq $secretFiles -or $secretFiles.Count -eq 0) {
    Write-Host "No .env files in '$resolvedSecretsDir'. Skipping secret injection."
    exit 0
}

foreach ($secretFile in $secretFiles) {
    $secretName = [System.IO.Path]::GetFileNameWithoutExtension($secretFile.Name)
    Write-Host "Applying secret '$secretName' from '$($secretFile.FullName)'..."
    & kubectl -n $Namespace create secret generic $secretName --from-env-file="$($secretFile.FullName)" --dry-run=client -o yaml | kubectl apply -f -
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to apply secret '$secretName'"
    }
}

Write-Host "Secrets injection completed."
