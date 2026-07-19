# Architecture

This document describes the current TradePulse architecture based on the live codebase.

Deep dives:
- `DATA_FLOW_MASSIVE_TO_FRONTEND.md` for exact market-data ingestion and UI streaming path
- `SAGA_AND_CONSISTENCY.md` for registration saga and cross-service consistency model

## 1. High-level topology

```text
React Frontend
    |
    v
API Gateway (4004)
    |
    +--> Auth Service (4005)
    +--> Customer Service (4000)
    +--> Stock Service (4003)
    +--> Order Service (4006)
    +--> Payment Service (4001)
    +--> Portfolio Service (4007)

Order Service gRPC calls:
    -> Payment Service (9002)
    -> Stock Service (9003)
    -> Portfolio Service / Portfolio Sync (9005)

Eventing:
Customer/Order/Payment/Portfolio Services -> Kafka topic `tradepulse.notifications` -> Notification Service (4008)

ML:
Stock Service <-> ML Service (4010)
```

## 2. Frontend architecture

The frontend is a Vite + React + TypeScript SPA.

### Main route map

- `/` — home / featured stocks
- `/login` — login and forgot password flows
- `/registration` — signup and customer registration
- `/about`
- `/analytics`
- `/portfolio`
- `/watchlist`
- `/checkout`
- `/payment`
- `/orders`
- `/account-management`
- `/wallet`
- `/stocks/:stockId/insights`

### App-level state providers

Mounted in `tradepulse-frontend/src/main.tsx`:

- `CartProvider`
- `OrdersProvider`
- `WatchlistProvider`
- `WalletProvider`
- `MarketStatusProvider`

### Frontend runtime patterns

- protected requests send `Authorization: Bearer <jwt>`
- frontend calls gateway-relative paths (`/api/...`, `/auth/...`)
- axios startup configuration handles timeout defaults and `401`-driven sign-out
- auth change propagation is same-tab and cross-tab
- market status is shared globally and cached in memory + localStorage
- featured stocks are streamed with SSE and cached in localStorage

## 3. Backend service ownership

Canonical backend service ids in this repository:

- `api-gateway`
- `auth-service`
- `cust-service`
- `stock-service`
- `order-service`
- `payment-service`
- `portfolio-service`
- `notification-service`
- `ml-service`

### API Gateway

Responsibilities:

- edge entry point for frontend traffic
- route dispatching to backend services
- JWT validation by calling `auth-service`
- authoritative user identity propagation through `X-User-Id`

Important rule:

- the gateway strips client-supplied `X-User-Id` and replaces it with the validated user id from `auth-service`

### Auth Service

Responsibilities:

- user registration
- login and JWT issuance
- token validation
- account credential reads and updates
- forgot-password code and reset flow

### Customer Service

Responsibilities:

- customer profile CRUD
- watchlist management
- registration saga coordination with auth-service
- customer Kafka event publishing

### Stock Service

Responsibilities:

- stock catalog and featured stocks
- stock search
- live featured-stock SSE stream
- market status REST + SSE endpoints
- market session caching
- stock insights / metrics over historical OHLC data
- fresh quote gRPC service for order-service

### Order Service

Responsibilities:

- cart management
- quote locking before payment
- order completion orchestration
- order history
- payment invocation
- portfolio sync invocation

### Payment Service

Responsibilities:

- wallet creation and balance management
- deposits and withdrawals
- purchase deduction for completed orders
- wallet transaction history
- payment records
- payment gRPC service

### Portfolio Service

Responsibilities:

- portfolio holdings read model
- portfolio buy/sell transaction history
- sell flow and PnL views
- portfolio sync gRPC service for completed orders

### Notification Service

Responsibilities:

- consumes notification events from Kafka topic `tradepulse.notifications`
- fetches user email metadata from auth-service
- sends email notifications asynchronously

### ML Service

Responsibilities:

- trains forecasting/classification models from stock-service data
- serves prediction endpoints consumed by stock-service
- supports startup and scheduled retraining

### Analytics Service

Responsibilities today:

- consumes protobuf customer events from Kafka
- logs received events

It currently acts as the starting point of analytics/event processing.

## 4. Key request flows

### A. Registration flow

1. Frontend posts to `/api/customers/register`
2. Gateway routes to customer service without JWT requirement
3. Customer service calls auth-service to create auth user
4. Customer service stores customer profile
5. Customer service publishes a customer event to Kafka
6. Notification service consumes notification events and sends user email alerts

### B. Login flow

1. Frontend posts to `/auth/login`
2. Auth service verifies credentials
3. Auth service returns JWT
4. Frontend stores token in localStorage or sessionStorage
5. Subsequent protected calls include bearer token

### C. Protected API call flow

1. Frontend sends bearer token
2. Gateway calls `/validate` on auth-service
3. Auth service validates token and returns authenticated user id
4. Gateway injects trusted `X-User-Id`
5. Downstream services use that header for ownership-scoped operations

### D. Home page stock flow

1. Frontend reads cached featured stocks if present
2. Frontend connects to `/api/stocks/stream/featured`
3. Stock service pushes stock updates via SSE
4. Frontend keeps cache updated for fast reloads

### E. Market status flow

1. Frontend `MarketStatusProvider` reads in-memory/local cache if fresh
2. Frontend fetches `/api/stocks/market-status`
3. Frontend subscribes to `/api/stocks/stream/market-status`
4. Stock service broadcasts backend-cached market-session state
5. Frontend preserves current fresh values against transient fallback responses

### F. Checkout and order completion flow

1. Frontend locks quote through order-service
2. Order-service resolves fresh quote data via stock-service gRPC
3. Order-service persists the order
4. Order-service triggers payment-service gRPC payment completion
5. Order-service triggers customer-service gRPC portfolio sync
6. Cart is cleared after successful completion

## 5. Runtime integration styles

### REST through gateway

Used for all frontend-facing operations.

### gRPC for internal synchronous orchestration

Used for:

- order payment completion
- fresh stock quote lookup
- completed-order portfolio synchronization

### Kafka for asynchronous events

Used for domain notification events published by multiple services and consumed by notification-service.

### SSE for live UI data

Used for:

- featured stocks stream
- market status stream

## 6. Persistence model

Each major service owns its own PostgreSQL database container in local Docker.

- auth-service-db
- cust-service-db
- order-service-db
- payment-service-db
- stock-service-db

This is a service-owned data model, not a shared-schema monolith.

## 7. Current strengths

- clear service ownership boundaries
- frontend uses one gateway entry point
- stock live-data concerns are isolated from order logic
- order-service is the checkout orchestration boundary
- service-specific databases reduce direct coupling
- SSE is used where user-visible freshness matters
- app-level market status and stock caches reduce avoidable loading states

## 8. Planned next architecture upgrades

- centralized metrics, tracing, and dashboards
- edge rate limiting and abuse controls
- refresh-token or shorter access-token strategy
- stronger automated integration testing between services
- notification templates/channel expansion (email + optional push/SMS)
- ML model governance and drift monitoring
- production-grade secrets management outside `.env`

