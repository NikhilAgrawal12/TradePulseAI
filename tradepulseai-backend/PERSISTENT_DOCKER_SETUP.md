# Persistent Backend Containers

This setup gives you **permanent data persistence** across container restarts and recreations.

## Why your data was disappearing

Your old DB containers used anonymous volumes. When containers were recreated, they attached new anonymous volumes, so tables looked empty.

This setup uses **named volumes** for every database:

- `auth_db_data`
- `cust_db_data`
- `order_db_data`
- `payment_db_data`
- `stock_db_data`

Named volumes survive `stop/start` and `down` (without `-v`).

## Files added

- `docker-compose.persistent.yml`
- `scripts/up-persistent.ps1`
- `scripts/stop-persistent.ps1`
- `scripts/start-persistent.ps1`

## First-time start

Run from `tradepulseai-backend`:

```powershell
.\scripts\up-persistent.ps1
```

This script:

- stops legacy conflicting containers (same ports)
- starts the persistent stack with Docker Compose

## Daily usage (safe)

Stop services (data kept):

```powershell
.\scripts\stop-persistent.ps1
```

Start services again (data kept):

```powershell
.\scripts\start-persistent.ps1
```

## Inspect status

```powershell
docker compose -p tradepulse-persistent -f .\docker-compose.persistent.yml ps
```

## Important warnings

Do **not** run this if you want to keep DB data:

```powershell
docker compose -p tradepulse-persistent -f .\docker-compose.persistent.yml down -v
```

`-v` removes named volumes and permanently deletes database data.

## If you need a full reset intentionally

```powershell
docker compose -p tradepulse-persistent -f .\docker-compose.persistent.yml down -v
.\scripts\up-persistent.ps1
```

