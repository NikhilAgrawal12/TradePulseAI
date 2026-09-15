param(
    [switch]$Force,
    [switch]$StartAfterReset,
    [string]$ConfirmText
)

$ErrorActionPreference = "Stop"

$projectName = if ($env:TRADEPULSE_COMPOSE_PROJECT) { $env:TRADEPULSE_COMPOSE_PROJECT } else { "tradepulse-backend" }
$composeFile = Join-Path $PSScriptRoot "..\docker-compose.persistent.yml"
$envFile = Join-Path $PSScriptRoot "..\.env"

$persistentVolumes = @(
    "tradepulse-backend_auth_db_data",
    "tradepulse-backend_cust_db_data",
    "tradepulse-backend_order_db_data",
    "tradepulse-backend_payment_db_data",
    "tradepulse-backend_portfolio_db_data",
    "tradepulse-backend_stock_db_data",
    "tradepulse-backend_analytics_db_data",
    "tradepulse-backend_kafka_data",
    "tradepulse-backend_ml_model_data"
)

if (-not $Force) {
    if ([string]::IsNullOrWhiteSpace($ConfirmText)) {
        $ConfirmText = Read-Host "This will DELETE all local persistent TradePulse data. Type RESET to continue"
    }

    if ($ConfirmText -ne "RESET") {
        Write-Host "Reset cancelled. No data was deleted."
        exit 0
    }
}

Write-Host "Stopping and removing persistent stack containers..."
docker compose --env-file $envFile -p $projectName -f $composeFile down --remove-orphans

Write-Host "Removing persistent volumes..."
foreach ($volume in $persistentVolumes) {
    docker volume rm -f $volume | Out-Null
}

Write-Host "Recreating empty persistent volumes..."
foreach ($volume in $persistentVolumes) {
    docker volume create $volume | Out-Null
}

if ($StartAfterReset) {
    Write-Host "Starting fresh stack..."
    & (Join-Path $PSScriptRoot "up-persistent.ps1")
} else {
    Write-Host "Reset complete. Start when ready with:"
    Write-Host ("`"{0}`"" -f (Join-Path $PSScriptRoot "up-persistent.ps1"))
}

