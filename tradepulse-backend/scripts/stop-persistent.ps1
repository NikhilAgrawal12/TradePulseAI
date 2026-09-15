$ErrorActionPreference = "Stop"

$projectName = if ($env:TRADEPULSE_COMPOSE_PROJECT) { $env:TRADEPULSE_COMPOSE_PROJECT } else { "tradepulse-backend" }
$composeFile = Join-Path $PSScriptRoot "..\docker-compose.persistent.yml"
$envFile = Join-Path $PSScriptRoot "..\.env"

Write-Host "Stopping persistent backend stack (data preserved). Avoid manual 'docker compose down -v' if you want to keep local data."
docker compose --env-file $envFile -p $projectName -f $composeFile stop

