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

Write-Host "Starting already-created persistent backend stack..."
foreach ($volume in $persistentVolumes) {
	docker volume create $volume | Out-Null
}
docker compose --env-file $envFile -p $projectName -f $composeFile start
Wait-ForServiceHealth -ServiceName "stock-service" -TimeoutSeconds 180
Wait-ForServiceHealth -ServiceName "analytics-service" -TimeoutSeconds 180

