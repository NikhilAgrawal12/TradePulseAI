param(
    [switch]$BuildImages
)

$ErrorActionPreference = "Stop"

$projectName = if ($env:TRADEPULSE_COMPOSE_PROJECT) { $env:TRADEPULSE_COMPOSE_PROJECT } else { "tradepulse-backend" }
$composeFile = Join-Path $PSScriptRoot "..\docker-compose.persistent.yml"
$envFile = Join-Path $PSScriptRoot "..\.env"

function Wait-ForServiceHealth {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [int]$TimeoutSeconds = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $containerId = docker compose --env-file $envFile -p $projectName -f $composeFile ps -q $ServiceName
        if ([string]::IsNullOrWhiteSpace($containerId)) {
            Start-Sleep -Seconds 2
            continue
        }

        $healthStatus = docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $containerId 2>$null
        if ($healthStatus -eq "healthy" -or $healthStatus -eq "running") {
            Write-Host "$ServiceName is $healthStatus"
            return
        }

        if ($healthStatus -eq "unhealthy" -or $healthStatus -eq "exited") {
            throw "$ServiceName is $healthStatus. Check logs: docker compose --env-file $envFile -p $projectName -f $composeFile logs --tail=120 $ServiceName"
        }

        Start-Sleep -Seconds 3
    }

    throw "Timed out waiting for $ServiceName to become healthy."
}

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
if ($BuildImages) {
    docker compose --env-file $envFile -p $projectName -f $composeFile up -d --build
} else {
    docker compose --env-file $envFile -p $projectName -f $composeFile up -d
}

Wait-ForServiceHealth -ServiceName "stock-service" -TimeoutSeconds 240
Wait-ForServiceHealth -ServiceName "analytics-service" -TimeoutSeconds 240

Write-Host "Done. Use this to check status:"
Write-Host "docker compose --env-file $envFile -p $projectName -f $composeFile ps"
Write-Host "Tip: pass -BuildImages only when Dockerfiles/dependencies changed."

