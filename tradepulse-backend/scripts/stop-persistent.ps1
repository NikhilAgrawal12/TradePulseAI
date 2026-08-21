$ErrorActionPreference = "Stop"

$projectName = if ($env:TRADEPULSE_COMPOSE_PROJECT) { $env:TRADEPULSE_COMPOSE_PROJECT } else { "tradepulse-backend" }
$composeFile = Join-Path $PSScriptRoot "..\docker-compose.persistent.yml"
$envFile = Join-Path $PSScriptRoot "..\.env"

Write-Host "Stopping persistent backend stack (data is preserved)..."
docker compose --env-file $envFile -p $projectName -f $composeFile stop

