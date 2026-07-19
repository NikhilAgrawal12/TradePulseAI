$ErrorActionPreference = "Stop"

$projectName = "tradepulse-persistent"
$composeFile = Join-Path $PSScriptRoot "..\docker-compose.persistent.yml"
$envFile = Join-Path $PSScriptRoot "..\.env"

# Stop legacy containers that commonly conflict on the same ports.
$legacyContainers = @(
    "auth-service",
    "customer-service",
    "order-service",
    "payment-service",
    "stock-service",
    "analytics-service",
    "api-gateway",
    "auth-service-db",
    "customer-service-db",
    "order-service-db",
    "payment-service-db",
    "stock-service-db",
    "tradepulse_postgres_cust",
    "tradepulse_postgres_stock",
    "tradepulse_kafka",
    "tradepulse_zookeeper",
    "tradepulse_kafka_ui"
)

foreach ($name in $legacyContainers) {
    $id = docker ps -q --filter "name=^$name$"
    if ($id) {
        Write-Host "Stopping legacy container: $name"
        docker stop $name | Out-Null
    }
}

Write-Host "Starting persistent backend stack..."
docker compose --env-file $envFile -p $projectName -f $composeFile up -d --build

Write-Host "Done. Use this to check status:"
Write-Host "docker compose --env-file $envFile -p $projectName -f $composeFile ps"

