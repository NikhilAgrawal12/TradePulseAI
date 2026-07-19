# API Surface

This document summarizes the main externally used REST routes, the auth model, the SSE endpoints, and the internal gRPC contracts.

## 1. Authentication model

### Frontend-visible auth behavior

Protected frontend calls send:

- `Authorization: Bearer <jwt>`
- `X-User-Id: <decoded user id>`

### Actual trust model

The backend no longer trusts the client-provided `X-User-Id`.

Runtime flow:

1. API Gateway validates the bearer token by calling `auth-service`
2. Auth service returns the authenticated user id
3. Gateway strips any incoming `X-User-Id`
4. Gateway injects the validated `X-User-Id` before forwarding the request

This is the real authorization boundary for user-owned data.

## 2. Public frontend-facing REST routes

### Auth routes

Routed through gateway to auth-service.

- `POST /auth/login`
- `GET /auth/validate`
- `POST /auth/register`
- `POST /auth/forgot-password/request-code`
- `POST /auth/forgot-password/verify-code`
- `POST /auth/forgot-password/reset`
- `GET /auth/users/{userId}`
- `GET /auth/users/{userId}/credentials`
- `PUT /auth/users/{userId}/credentials`
- `DELETE /auth/users/{userId}`

### Customer routes

- `POST /api/customers/register`
- `GET /api/customers/user/{userId}`
- `PUT /api/customers/{userId}`
- `DELETE /api/customers/{userId}`

### Watchlist routes

- `GET /api/watchlist`
- `POST /api/watchlist/items`
- `DELETE /api/watchlist/items/{stockId}`
- `DELETE /api/watchlist`

### Portfolio routes

- `GET /api/portfolio`
- `POST /api/portfolio/sell/{stockId}`

### Stock routes

- `GET /api/stocks`
- `GET /api/stocks/featured`
- `POST /api/stocks/featured/refresh-once`
- `GET /api/stocks/featured/health`
- `GET /api/stocks/{id}`
- `GET /api/stocks/{id}/insights`
- `GET /api/stocks/symbol/{symbol}`
- `GET /api/stocks/search`
- `GET /api/stocks/market-status`

### Cart and order routes

- `GET /api/cart`
- `POST /api/cart/items`
- `PUT /api/cart/items/{stockId}`
- `DELETE /api/cart/items/{stockId}`
- `DELETE /api/cart`
- `POST /api/cart/lock-quote`
- `POST /api/cart/complete-order`
- `GET /api/orders`
- `GET /api/orders/paged`

### Wallet routes

- `GET /api/wallet/me`
- `POST /api/wallet/deposit`
- `POST /api/wallet/withdraw`
- `GET /api/wallet/transactions`
- `GET /api/wallet/transactions/paged`

### ML routes (internal/service-to-service)

- Served by `ml-service` (FastAPI)
- `POST /v1/train`
- `GET /v1/predictions/{stock_id}`

## 3. SSE endpoints

### Featured stocks stream

- `GET /api/stocks/stream/featured`
- optional query parameter: `query`

Behavior:
- used by the frontend for the live featured stock list and search overlay behavior

### Market status stream

- `GET /api/stocks/stream/market-status`

Behavior:
- used by the global market-status provider
- frontend keeps a REST bootstrap and SSE subscription together

## 4. gRPC contracts in use

The codebase currently uses three real gRPC APIs.

### `OrderPaymentService`

Caller:
- order-service

Server:
- payment-service

Purpose:
- complete wallet-backed payment for an order

### `StockQuoteService`

Caller:
- order-service

Server:
- stock-service

Purpose:
- resolve fresh quote data and validate stock at checkout time

### `PortfolioSyncService`

Caller:
- order-service

Server:
- portfolio-service

Purpose:
- sync successful completed orders into portfolio holdings and transactions

## 5. Kafka event contract

There is also protobuf/JSON event usage outside gRPC:

- customer-service, order-service, payment-service, and portfolio-service publish notification events to Kafka topic `tradepulse.notifications`
- `notification-service` consumes `tradepulse.notifications` and sends email notifications
- `notification-service` does not expose frontend-facing REST routes in the current design

Important note:
- `customer_event.proto` is protobuf-based messaging, not a gRPC API

## 6. OpenAPI aggregation routes

Gateway-exposed docs routes in current config:

- `/api-docs/customers`
- `/api-docs/stocks`
- `/api-docs/orders`

These are rewrites to the downstream service `/v3/api-docs` endpoints.

## 7. Pagination behavior

Current paginated endpoints in code include:

- `GET /api/orders/paged`
- `GET /api/wallet/transactions/paged`

Both normalize page and size values on the backend and cap page size at 50.

## 8. Error behavior

Patterns currently present:

- `400` for malformed request data
- `401` for invalid or missing auth on protected routes
- `404` for missing resources such as users/customers
- `409` for business conflicts such as closed-market sell attempts or wallet insufficiency semantics
- `500` for unexpected failures

Frontend note:
- a `401` now clears local auth state automatically so the app does not keep a broken stale session

## 9. Recommended usage rules for future contributors

- do not bypass the API gateway for frontend traffic
- do not trust client-supplied ownership headers in downstream services
- keep user-scoped operations tied to the validated `X-User-Id`
- use SSE only for truly live user-facing feeds
- keep synchronous checkout orchestration inside order-service

