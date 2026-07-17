# Data Flow: Massive API to Frontend

This document explains how market data moves from Massive to the TradePulse UI, with exact runtime boundaries.

## 1. End-to-end path (high level)

```text
Massive Delayed WebSocket (wss://delayed.massive.com/stocks)
    -> stock-service AllStocksLastValueCacheService
    -> stock-service cache + stock quote/search services
    -> stock-service FeaturedStockSSEService
    -> API Gateway (/api/stocks/**)
    -> Frontend EventSource (/api/stocks/stream/featured)
    -> Home page cards (featured + searched)
```

## 2. Backend ingestion from Massive

Primary component:
- `tradepulse-backend/stock-service/src/main/java/com/tradepulse/stockservice/service/AllStocksLastValueCacheService.java`

Key behavior:
- connects to `wss://delayed.massive.com/stocks`
- authenticates with `massive.api.key`
- subscribes to aggregate channels in symbol chunks
- parses incoming events and updates in-memory/persistent cache
- publishes stock-cache update events for downstream stock-service components

Important operational note:
- if `massive.api.key` is missing, websocket cache startup is disabled and service logs a warning.

## 3. Stock-service data serving layer

Public REST/SSE controller:
- `tradepulse-backend/stock-service/src/main/java/com/tradepulse/stockservice/controller/StockController.java`

Endpoints used by UI:
- `GET /stocks/featured` (bootstrap featured list)
- `GET /stocks/search?query=...` (immediate search fetch)
- `GET /stocks/stream/featured` (SSE live updates; optional `query`)
- `GET /stocks/market-status`
- `GET /stocks/stream/market-status`

Featured/search stream behavior:
- one SSE endpoint serves both modes
- no `query`: featured list stream
- with `query`: search-filtered live stream

## 4. Gateway routing and trust boundary

Gateway fronts all frontend stock traffic:
- frontend calls `/api/stocks/...`
- gateway forwards to stock-service routes under `/stocks/...`

Result:
- frontend never calls stock-service container directly
- routing remains consistent for local and hosted environments

## 5. Frontend home-page runtime flow

Main hook:
- `tradepulse-frontend/src/utils/useStreamedStocks.ts`

Behavior today:
1. if no search term:
   - boot from local cache (if available)
   - optional bootstrap fetch from `/api/stocks/featured`
   - subscribe to `/api/stocks/stream/featured`
2. if search term exists:
   - immediate fetch from `/api/stocks/search?query=...`
   - subscribe to `/api/stocks/stream/featured?query=...` for live updates
3. stale-response protection:
   - request serial guard prevents older search responses from replacing newer query results

Home-page usage:
- `tradepulse-frontend/src/pages/home/HomePage.tsx`
- UI binds `searchTerm` to `setSearchTerm`, renders `stocks` from the hook

## 6. Latency profile: featured vs searched

- Featured mode is usually fastest on first paint because of local cache + SSE bootstrap.
- Search mode now has an immediate REST fetch before SSE updates, which significantly reduces perceived delay.
- Both modes then continue with SSE-driven live updates.

## 7. Failure modes and fallbacks

Common cases:
- SSE transient disconnect:
  - frontend reconnect loop resumes stream
  - existing stock list is retained during short interruptions
- Massive key missing/invalid:
  - backend websocket cache cannot refresh from source
  - logs indicate auth/connection failure
- empty/transient stream payloads:
  - frontend avoids wiping a non-empty list during reconnect edge cases

## 8. Quick verification checklist

1. Start backend and frontend.
2. Open home page and confirm featured cards update over time.
3. Search for a symbol (for example: `AAPL`) and confirm quick result appearance.
4. Keep search active and verify values continue updating.
5. Clear search and confirm featured stream resumes.

## 9. Design rationale

The architecture splits responsibilities cleanly:
- stock-service owns upstream market-data complexity
- gateway owns frontend entry routing
- frontend owns rendering and lightweight cache for UX smoothness

This makes the system understandable for reviewers while preserving real-time behavior.

