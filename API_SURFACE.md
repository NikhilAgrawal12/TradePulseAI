# API Surface

This document is the authoritative reference for all active REST endpoints, SSE streams, gRPC contracts, and internal-only routes in TradePulse. Only endpoints that are actively called — from the frontend or from another backend service — are listed here. Unused endpoints have been removed from the codebase.

---

## 1. Authentication model

### Client-side headers sent by the frontend

Protected requests include:

- `Authorization: Bearer <jwt>`
- `X-User-Id: <decoded user id>`

### Actual trust model (server-side)

Downstream services do **not** trust the client-supplied `X-User-Id`. The API Gateway owns the authorization boundary:

1. Gateway validates the bearer token by calling `auth-service GET /validate`
2. Auth service returns the authenticated user id
3. Gateway strips any incoming `X-User-Id` header
4. Gateway injects the validated `X-User-Id` before forwarding the request

---

## 2. Auth routes

Routed through the gateway directly to `auth-service`. These bypass the `/api` prefix.

| Method | Path | Description | Auth required |
|--------|------|-------------|---------------|
| `POST` | `/auth/login` | Authenticate with email + password, returns JWT | No |
| `POST` | `/auth/register` | Internal — used by customer-service registration saga | No |
| `POST` | `/auth/forgot-password/request-code` | Send reset code to email | No |
| `POST` | `/auth/forgot-password/verify-code` | Validate the reset code | No |
| `POST` | `/auth/forgot-password/reset` | Set new password after code verification | No |
| `GET` | `/auth/validate` | Validate JWT token — used by the gateway filter | Internal |
| `GET` | `/auth/users/{userId}` | Fetch user record by ID — used by notification-service and API gateway | Internal |
| `GET` | `/auth/me/credentials` | Get authenticated user's login credentials (email, username) | Yes |
| `PUT` | `/auth/me/credentials` | Update email or username | Yes |
| `PUT` | `/auth/me/password` | Change password | Yes |
| `DELETE` | `/auth/users/{userId}` | Delete user account — called internally by customer-service during customer deletion saga | Internal |

### Request bodies

**POST /auth/login**
```json
{ "email": "string", "password": "string" }
```

**POST /auth/forgot-password/request-code**
```json
{ "email": "string" }
```

**POST /auth/forgot-password/verify-code**
```json
{ "email": "string", "code": "string" }
```

**POST /auth/forgot-password/reset**
```json
{ "email": "string", "code": "string", "newPassword": "string" }
```

**PUT /auth/me/credentials**
```json
{ "email": "string", "username": "string" }
```

**PUT /auth/me/password**
```json
{ "currentPassword": "string", "newPassword": "string" }
```

---

## 3. Customer routes

Routed through the gateway under `/api/customers` → `customer-service`.

| Method | Path | Description | Caller |
|--------|------|-------------|--------|
| `POST` | `/api/customers/register` | Register new user + customer profile in a single saga (creates auth user and customer record) | Frontend |
| `GET` | `/api/customers/me` | Get authenticated user's customer profile | Frontend, API Gateway |
| `PUT` | `/api/customers/me` | Update authenticated user's customer profile | Frontend |
| `GET` | `/api/customers/user/{userId}` | Get customer by userId — internal service-to-service only | notification-service, order-service, portfolio-service |

### Request body — POST /api/customers/register
```json
{
  "email": "string",
  "username": "string",
  "password": "string",
  "firstName": "string",
  "lastName": "string",
  "phone": "string"
}
```

### Request body — PUT /api/customers/me
```json
{
  "firstName": "string",
  "lastName": "string",
  "phone": "string"
}
```

---

## 4. Profile aggregation route

Exposed directly by the API Gateway (not forwarded to a downstream service).

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/profile` | Aggregates data from `auth-service` (credentials) and `customer-service` (profile) into a single response |

This endpoint requires a valid bearer token. The gateway calls `GET /auth/me/credentials` and `GET /customers/me` in parallel and merges the results.

---

## 5. Watchlist routes

Routed through the gateway under `/api/watchlist` → `customer-service`.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/watchlist` | Get all stocks on the authenticated user's watchlist |
| `POST` | `/api/watchlist/items` | Add a stock to the watchlist |
| `DELETE` | `/api/watchlist/items/{stockId}` | Remove a specific stock from the watchlist |
| `DELETE` | `/api/watchlist` | Clear the entire watchlist |

### Request body — POST /api/watchlist/items
```json
{ "stockId": "number" }
```

---

## 6. Stock routes

Routed through the gateway under `/api/stocks` → `stock-service`.

| Method | Path | Description | Caller |
|--------|------|-------------|--------|
| `GET` | `/api/stocks/featured` | Get top 50 featured stocks ordered by sort_order | Frontend |
| `GET` | `/api/stocks/search` | Search stocks by symbol or name (query param: `query`) | Frontend |
| `GET` | `/api/stocks/{id}` | Get a single stock by ID | portfolio-service (internal, for sell notifications) |
| `GET` | `/api/stocks/market-status` | Get the current cached market session status (OPEN/CLOSED/PRE/AFTER) | Frontend, portfolio-service |

### Query parameters

**GET /api/stocks/search**
- `query` (optional, string) — filters stocks by symbol or name prefix; returns all featured stocks if omitted

---

## 7. Analytics routes

Routed through the gateway under `/api/analytics` → `analytics-service` (Python FastAPI).

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/analytics/stocks/{stockId}/insights` | Get analytics insights for a specific stock (sentiment, volume trends, news) |
| `GET` | `/api/analytics/predictions/{stockId}` | Get ML prediction for a specific stock |
| `GET` | `/api/analytics/news` | Get latest analytics news items |

### Query parameters

**GET /api/analytics/news**
- `limit` (optional, integer, default `10`, range `1–100`) — number of news items to return

### ML health endpoint (internal/ops)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Analytics service health — model load status, training state, sync freshness |

---

## 8. Cart and order routes

Routed through the gateway under `/api/cart` and `/api/orders` → `order-service`.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/cart` | Get the authenticated user's current cart |
| `POST` | `/api/cart/items` | Add a stock item to the cart |
| `PUT` | `/api/cart/items/{stockId}` | Update quantity of a stock in the cart |
| `DELETE` | `/api/cart/items/{stockId}` | Remove a specific stock from the cart |
| `DELETE` | `/api/cart` | Clear the entire cart |
| `POST` | `/api/cart/lock-quote` | Lock in live price quotes for all cart items before checkout |
| `POST` | `/api/cart/complete-order` | Complete the checkout: debit wallet, record order, and emit downstream order events |
| `GET` | `/api/orders` | Get all orders for the authenticated user (full list, no pagination) |
| `GET` | `/api/orders/paged` | Get paginated orders for the authenticated user |

### Request body — POST /api/cart/items
```json
{ "stockId": "number", "quantity": "number" }
```

### Request body — PUT /api/cart/items/{stockId}
```json
{ "quantity": "number" }
```

### Pagination — GET /api/orders/paged

| Query param | Type | Default | Max | Description |
|-------------|------|---------|-----|-------------|
| `page` | integer | `0` | — | Zero-based page index |
| `size` | integer | `10` | `50` | Number of orders per page |

---

## 9. Wallet routes

Routed through the gateway under `/api/wallet` → `payment-service`.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/wallet/me` | Get the authenticated user's wallet balance and details |
| `POST` | `/api/wallet/deposit` | Deposit funds into the wallet |
| `POST` | `/api/wallet/withdraw` | Withdraw funds from the wallet |
| `GET` | `/api/wallet/transactions` | Get all wallet transactions (full list, no pagination) |
| `GET` | `/api/wallet/transactions/paged` | Get paginated wallet transactions |

### Request body — POST /api/wallet/deposit
```json
{ "amount": "number" }
```

### Request body — POST /api/wallet/withdraw
```json
{ "amount": "number" }
```

### Pagination — GET /api/wallet/transactions/paged

| Query param | Type | Default | Max | Description |
|-------------|------|---------|-----|-------------|
| `page` | integer | `0` | — | Zero-based page index |
| `size` | integer | `10` | `50` | Number of transactions per page |

---

## 10. Portfolio routes

Routed through the gateway under `/api/portfolio` → `portfolio-service`.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/portfolio` | Get the authenticated user's portfolio holdings |
| `POST` | `/api/portfolio/sell/{stockId}` | Sell shares of a stock from the portfolio |

### Pagination — GET /api/portfolio

| Query param | Type | Default | Max | Description |
|-------------|------|---------|-----|-------------|
| `page` | integer | `0` | — | Zero-based page index |
| `size` | integer | `10` | `50` | Number of holdings per page |

### Request body — POST /api/portfolio/sell/{stockId}
```json
{ "quantity": "number" }
```

---

## 11. SSE endpoints

### Featured stocks stream

```
GET /api/stocks/stream/featured
```

| Query param | Description |
|-------------|-------------|
| `query` (optional) | Filter the live featured stock list by symbol or name |

**Behavior:**
- Pushes live stock price updates from the Massive WebSocket feed
- Frontend uses this for the live market ticker and the search overlay
- When `query` is set, the stream filters results to matching stocks; omit to receive all featured stocks

### Market status stream

```
GET /api/stocks/stream/market-status
```

**Behavior:**
- Pushes market session state changes (`OPEN`, `CLOSED`, `PRE_MARKET`, `AFTER_HOURS`)
- Frontend bootstraps with `GET /api/stocks/market-status` then subscribes to this SSE stream to receive live changes
- Also consumed by portfolio-service via REST (non-stream) to gate sell eligibility

---

## 12. ML model training (operational scripts only)

The analytics service exposes one HTTP endpoint used by operational scripts for manual model training triggers. It is **not** called from the frontend or from any other backend service.

| Method | Path | Caller |
|--------|------|--------|
| `POST` | `/v1/train` | `run_train.py`, `_train_trigger.py` scripts |

**POST /v1/train — Request body**
```json
{ "days_back": 365, "horizon_days": 5 }
```

**Response** includes: `selected_model`, `trained_rows`, `horizon_days`, `metrics[]` (cv_f1, test_f1, balanced_accuracy, precision, recall per model).

> Routine retraining is handled automatically by the internal scheduler thread — this endpoint is for manual/dev triggers only.

---

## 13. gRPC contracts

The codebase uses two active gRPC APIs for synchronous inter-service calls during checkout and sell flows.

### `StockQuoteService`

| | |
|--|--|
| **Caller** | order-service |
| **Server** | stock-service (port 9003) |
| **Purpose** | Resolve fresh live quote data and validate stock eligibility at checkout time |

### `OrderPaymentService`

| | |
|--|--|
| **Caller** | order-service (checkout), portfolio-service (sell settlement) |
| **Server** | payment-service (port 9002) |
| **Purpose** | Debit wallet for buy orders (`completePayment`); credit wallet after sell (`settleSell`) |

---

## 14. Kafka event contract

Async domain events are delivered through Kafka rather than direct REST/gRPC fan-out after checkout.

- **Topic**: `tradepulse.orders.events`
- **Producer**: order-service outbox relay
- **Consumer**: portfolio-service
- **Format**: JSON with `eventType`, `eventId`, `orderId`, `userId`, `timestamp`, `data`

- **Topic**: `tradepulse.notifications.events`
- **Producers**: customer-service, order-service, payment-service, portfolio-service
- **Consumer**: notification-service
- **Format**: JSON with `eventType`, `userId`, `timestamp`, `data`

The notification-service does **not** expose any frontend-facing REST routes.

---

## 15. OpenAPI aggregation routes (gateway-exposed)

| Path | Upstream |
|------|----------|
| `/api-docs/customers` | customer-service `/v3/api-docs` |
| `/api-docs/stocks` | stock-service `/v3/api-docs` |
| `/api-docs/orders` | order-service `/v3/api-docs` |

---

## 16. Error codes

| Code | Meaning |
|------|---------|
| `400` | Malformed or invalid request data |
| `401` | Missing or expired bearer token — frontend clears local auth state automatically |
| `403` | Authenticated but not authorized (e.g. accessing another user's resource) |
| `404` | Requested resource not found |
| `409` | Business conflict — e.g. sell attempt when market is closed, insufficient wallet balance |
| `503` | ML model not yet trained / prediction snapshot unavailable |
| `500` | Unexpected server error |

---

## 17. Usage rules for contributors

- Do not bypass the API gateway for frontend traffic
- Do not trust client-supplied `X-User-Id` headers in downstream services — rely only on the gateway-injected value
- Keep user-scoped operations tied to the validated `X-User-Id`
- Use SSE only for truly live user-facing feeds
- Keep synchronous checkout orchestration inside order-service
- All new public endpoints must go through the API gateway
- Do not add endpoints that aren't called by a known consumer (frontend, another service, or an operational script)
