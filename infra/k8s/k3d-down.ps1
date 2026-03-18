param(
    [string]$ClusterName = "edop"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not (Get-Command k3d -ErrorAction SilentlyContinue)) {
    throw "Command 'k3d' is not available in PATH."
}

$existingClusters = @()
try {
    $existingClusters = & k3d cluster list -o json 2>$null | ConvertFrom-Json
}
catch {
    $existingClusters = @()
}

$clusterExists = $false
foreach ($cluster in $existingClusters) {
    if ($cluster.name -eq $ClusterName) {
        $clusterExists = $true
        break
    }
}

if ($clusterExists) {
    Write-Host "Deleting k3d cluster '$ClusterName'..."
    & k3d cluster delete $ClusterName | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to delete cluster '$ClusterName'"
    }
    Write-Host "Cluster '$ClusterName' deleted."
}
else {
    Write-Host "Cluster '$ClusterName' does not exist. Nothing to delete."
}
