# Operations Runbook

This runbook is for running, checking, and supporting TradePulse in local or early hosted environments.

## 1. Runtime inventory

### Stateful components

- PostgreSQL databases:
  - auth-service-db
  - customer-service-db
  - order-service-db
  - payment-service-db
  - stock-service-db
- Kafka broker with persisted volume

### Stateless components

- api-gateway
- auth-service
- customer-service
- order-service
- payment-service
- portfolio-service
- stock-service
- notification-service
- ml-service
- frontend dev/build artifact

## 2. Critical environment variables

### Shared infra

- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_DB`

### Auth

- `JWT_SECRET`
- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`
- `MAIL_SMTP_AUTH`
- `MAIL_SMTP_STARTTLS_ENABLE`
- `MAIL_SMTP_STARTTLS_REQUIRED`
- `MAIL_SMTP_CONNECTION_TIMEOUT`
- `MAIL_SMTP_TIMEOUT`
- `MAIL_SMTP_WRITE_TIMEOUT`

### Stock data

- `MASSIVE_API_KEY`

### Gateway

- `AUTH_SERVICE_URL`

### Internal gRPC overrides if needed

- `ORDER_PAYMENT_SERVICE_ADDRESS`
- `ORDER_PAYMENT_SERVICE_GRPC_PORT`
- `STOCK_SERVICE_GRPC_ADDRESS`
- `STOCK_SERVICE_GRPC_PORT`
- `PORTFOLIO_SYNC_SERVICE_ADDRESS`
- `PORTFOLIO_SYNC_SERVICE_GRPC_PORT`

## 3. Standard local operations

### Start stack

```powershell
Set-Location "C:\Users\nikhi\Desktop\TradePulse\tradepulse-backend\scripts"
.\up-persistent.ps1
```

### Start existing stack without rebuild

```powershell
Set-Location "C:\Users\nikhi\Desktop\TradePulse\tradepulse-backend\scripts"
.\start-persistent.ps1
```

### Stop stack but keep data

```powershell
Set-Location "C:\Users\nikhi\Desktop\TradePulse\tradepulse-backend\scripts"
.\stop-persistent.ps1
```

### Inspect compose status

```powershell
Set-Location "C:\Users\nikhi\Desktop\TradePulse\tradepulse-backend"
docker compose --env-file .env -p tradepulse-persistent -f docker-compose.persistent.yml ps
```

## 4. Health checks

### Gateway

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:4004/api/stocks/featured | Select-Object -ExpandProperty StatusCode
```

### Stock market status

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:4004/api/stocks/market-status | Select-Object -ExpandProperty Content
```

### Featured cache readiness

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:4004/api/stocks/featured/health | Select-Object -ExpandProperty Content
```

## 5. Logs and debugging

### Compose logs for a single service

```powershell
Set-Location "C:\Users\nikhi\Desktop\TradePulse\tradepulse-backend"
docker compose --env-file .env -p tradepulse-persistent -f docker-compose.persistent.yml logs --tail 200 stock-service
```

Swap `stock-service` with:
- `api-gateway`
- `auth-service`
- `customer-service`
- `order-service`
- `payment-service`
- `portfolio-service`
- `notification-service`
- `ml-service`

## 6. Incident hints by symptom

### Login succeeds but protected screens fail with unauthorized

Check:
- gateway logs
- auth-service `/validate` behavior
- token expiration
- browser storage contents

### Wallet screens fail or show empty data

Check:
- payment-service logs
- gateway routing for `/api/wallet/**`
- authenticated header propagation

### Order completion fails

Check:
- order-service logs first
- payment-service gRPC availability
- stock-service gRPC availability
- portfolio-service gRPC availability
- wallet balance and quote-lock behavior

### Portfolio looks stale or incomplete

Check:
- whether order completion succeeded fully
- portfolio-service sync logs
- stock-service quote fetch behavior used in portfolio reads

### Home page shows no stocks

Check:
- stock-service featured cache readiness
- SSE connection from browser devtools
- stock-service logs for refresh failures
- `MASSIVE_API_KEY`

### Market status stays on fallback

Check:
- `/api/stocks/market-status`
- `/api/stocks/stream/market-status`
- stock-service market status refresh logs
- whether backend timestamps are updating within the freshness window

## 7. Backup and restore guidance

Current local stack uses Docker named volumes.

Minimum production recommendation:

- schedule PostgreSQL backups per service database
- back up Kafka data only if event replay requirements exist
- document recovery order: databases first, then Kafka, then services

## 8. Deployment readiness checklist

Before broader release, confirm:

- all secrets are injected securely
- Java 21 runtime is pinned in deployment environment
- gateway is the only frontend-facing backend entry point
- JWT secret is strong and rotated by policy
- database backups are automated
- request/exception logging is centralized
- alerting exists for service downtime and database saturation
- integration tests cover auth, checkout, wallet, and portfolio flows

## 9. Current production gaps still worth addressing

The codebase is workable, but these should still be planned:

- metrics and tracing via actuator / OpenTelemetry
- edge rate limiting and abuse controls
- formal migration tooling instead of relying on schema auto-update
- resilient retry/circuit-breaker strategy for internal service calls
- more exhaustive automated test suites
- notification-service template/channel expansion
- ML monitoring and retraining governance

## 10. Support rule of thumb

When a user-visible issue happens, inspect in this order:

1. browser network panel
2. gateway logs
3. target service logs
4. downstream dependency status
5. database records for the affected user/order/wallet

