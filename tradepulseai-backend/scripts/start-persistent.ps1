$ErrorActionPreference = "Stop"

$projectName = "tradepulse-persistent"
$composeFile = Join-Path $PSScriptRoot "..\docker-compose.persistent.yml"
$envFile = Join-Path $PSScriptRoot "..\.env"

Write-Host "Starting already-created persistent backend stack..."
docker compose --env-file $envFile -p $projectName -f $composeFile start

