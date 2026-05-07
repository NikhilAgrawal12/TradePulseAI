# Quick Start Guide - TradePulseAI Real-Time Stock Data

## Prerequisites

- Docker & Docker Compose
- Java 21
- Node.js 18+
- Maven

## Step 1: Start Infrastructure

Start PostgreSQL in the background:

```bash
# From project root directory
docker-compose up -d

# Verify services are running
docker-compose ps

# Check logs
docker-compose logs -f postgres-stock
```

**You should see:**
- PostgreSQL running on port 5432

## Step 2: Start Backend Service

```bash
cd tradepulseai-backend/stock-service
mvn spring-boot:run
```

**You should see:**
```
Started StockServiceApplication in X.XXX seconds
```

The backend will:
- Create/update the database schema
- Start the WebSocket server on /ws/stocks
- Fetch stock data directly from Massive API and persist it into the database

## Step 3: Start Frontend

In a new terminal:

```bash
cd tradepulseai-frontend
npm install
npm run dev
```

**You should see:**
```
VITE v5.x.x ready in ... ms

➜  Local:   http://localhost:5173/
```

## Step 4: Verify Everything Works

### Check Backend API
```bash
curl http://localhost:4003/api/stocks
```

You'll see stock data with prices.

### Check Frontend
1. Open http://localhost:5173
2. You should see 15 stock cards on the home page
3. Open browser DevTools (F12) → Console
4. Look for logs like:
   ```
   [useStocks] Using cached stocks: 15
   [WebSocket] Connected to stock feed
   ```

### Trigger Daily Refresh Manually (Optional)

```bash
curl -X POST http://localhost:4003/test/refresh/daily
```

This pulls latest data directly from Massive API, stores it in PostgreSQL, and pushes updates to WebSocket clients.

## Step 5: Verify Data Persistence

Test that data survives a database restart:

1. Note a stock price (e.g., AAPL = $180.25)
2. Restart the database:
   ```bash
   docker-compose restart postgres-stock
   ```
3. Refresh the frontend (F5)
4. The price should still be there - data persisted!

## ⚠️ Important: Preserving Data When Stopping Containers

All services now use **Docker Named Volumes** to persist data:
- **PostgreSQL** → `postgres_stock_data`

### ✅ Correct Way to Stop & Restart (Data Preserved)
```bash
# Stop containers but keep volumes
docker-compose stop

# Start containers again - data is preserved!
docker-compose start
```

### ❌ Wrong Way (Data Lost)
```bash
# This REMOVES containers AND volumes - data is lost!
docker-compose down

# If you accidentally did this, use the -v flag to avoid volume deletion:
docker-compose down    # Don't use this unless you want to delete data
```

### Clean Up Everything (Delete Data)
Only use this when you want to start fresh:
```bash
docker-compose down -v
```

## Common Commands

Check backend health:
```bash
curl http://localhost:4003/actuator/health
```

View database:
```bash
docker exec -it tradepulse_postgres_stock psql -U postgres -d tradepulse_stock \
  -c "SELECT symbol, price FROM stocks LIMIT 5;"
```

View backend logs:
```bash
docker-compose logs -f stock-service
```

### Volume Management Commands
View all volumes:
```bash
docker volume ls | grep tradepulse
```

Inspect a volume to see its location:
```bash
docker volume inspect tradepulse_postgres_stock_data
```

Check volume disk usage:
```bash
docker system df -v
```

## Troubleshooting

**No stocks showing?**
- Check backend is running: `curl http://localhost:4003/api/stocks`
- Check database has data: `docker-compose ps`

**No frontend updates?**
- Check WebSocket connection in browser console (F12) 
- Look for: `[WebSocket] Connected to stock feed`
- Trigger refresh endpoint: `curl -X POST http://localhost:4003/test/refresh/daily`

**Data lost after DB restart?**
- This shouldn't happen with PostgreSQL persistence
- Check database connection in logs
- Verify PostgreSQL container is running

**WebSocket showing outdated prices?**
- Check latest market rows directly: `docker exec -it tradepulse_postgres_stock psql -U postgres -d tradepulse_stock -c "SELECT s.symbol, smd.close_price, smd.market_timestamp FROM stock_market_data smd JOIN stocks s ON s.stock_id = smd.stock_id ORDER BY smd.market_timestamp DESC LIMIT 5;"`

## Architecture: Daily Direct Ingestion

### Data Flow
```
Massive API → Stock Service (daily scheduler) → PostgreSQL → WebSocket → Frontend
```

## Enable Live Polygon.io Data

Set environment variables:
```bash
export POLYGON_API_KEY=your_api_key
export POLYGON_FETCH_ENABLED=true
```

Then restart backend. Stock prices will be fetched by the daily scheduler.

