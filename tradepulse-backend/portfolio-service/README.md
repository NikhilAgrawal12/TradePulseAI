# portfolio-service

Standalone microservice for portfolio holdings, portfolio transactions, sell operations, and order-completion portfolio synchronization.

## Responsibilities

- REST portfolio read API
- REST sell API
- gRPC `PortfolioSyncService` for completed order ingestion from `order-service`
- Owns `portfolio_holdings` and `portfolio_transactions` tables in `portfolio-service-db`

## Local compile

```powershell
cd "C:\Users\nikhi\Desktop\TradePulse\tradepulse-backend\portfolio-service"
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
.\mvnw clean compile
```

## Docker

```powershell
cd "C:\Users\nikhi\Desktop\TradePulse\tradepulse-backend"
docker compose -f docker-compose.persistent.yml up -d --build portfolio-service-db portfolio-service
```

## Ports

- HTTP: `4007`
- gRPC: `9005`
- DB host port: `5006`

## Main routes

- `GET /portfolio`
- `POST /portfolio/sell/{stockId}`

