# Backend Services

This document explains each backend service, its port, ownership, and integration style.

## 1. Service summary

| Service | HTTP Port | Other Ports | Main Role |
|---|---:|---:|---|
| API Gateway | 4004 | - | frontend entry point and auth edge |
| Auth Service | 4005 | - | login, registration, JWT validation, credentials |
| Customer Service | 4000 | 9004 gRPC | profiles, watchlist, portfolio, registration saga |
| Payment Service | 4001 | 9002 gRPC | wallets, wallet transactions, payments |
| Stock Service | 4003 | 9003 gRPC | stock catalog, search, SSE, market status, insights |
| Order Service | 4006 | - | cart, quote lock, checkout orchestration, orders |
| Analytics Service | 4002 | - | Kafka event consumption |

## 2. API Gateway

Path routing in code:

- `/auth/**` -> auth-service
- `/api/customers/register` -> customer-service
- `/api/customers/**` -> customer-service with JWT validation
- `/api/stocks/**` -> stock-service
- `/api/watchlist/**` -> customer-service with JWT validation
- `/api/cart/**` and `/api/orders/**` -> order-service with JWT validation
- `/api/wallet/**` -> payment-service with JWT validation

Current gateway behavior:

- validates bearer token through auth-service
- rejects missing or invalid bearer tokens on protected routes
- injects trusted `X-User-Id` after successful validation
- exposes selected service OpenAPI docs through rewritten routes

## 3. Auth Service

### Responsibilities

- login
- register auth user
- validate JWT
- fetch account credentials
- update account email
- forgot-password request / verify / reset

### Key endpoints

- `POST /login`
- `GET /validate`
- `POST /register`
- `POST /forgot-password/request-code`
- `POST /forgot-password/verify-code`
- `POST /forgot-password/reset`
- `GET /users/{userId}`
- `GET /users/{userId}/credentials`
- `PUT /users/{userId}/credentials`
- `DELETE /users/{userId}`

### Important implementation notes

- password hashing uses BCrypt
- JWT includes `sub`, `role`, and `userId`
- `/validate` now returns the authenticated user id for gateway trust propagation

## 4. Customer Service

### Responsibilities

- customer profile read/update/delete
- combined registration flow with auth-service
- watchlist CRUD
- portfolio read model
- portfolio sell operations
- portfolio sync gRPC server for completed orders
- Kafka publishing of customer events

### External REST shape

- `/customers/register`
- `/customers/user/{userId}`
- `/customers/{userId}`
- `/watchlist`
- `/watchlist/items`
- `/customers/portfolio`
- `/customers/portfolio/sell/{stockId}`

### Internal dependencies

- REST client to auth-service
- REST client to stock-service for quote/session reads in portfolio views
- gRPC server for portfolio synchronization
- Kafka producer for customer events

### Security behavior

- customer profile routes now verify that path `userId` matches the authenticated `X-User-Id`

## 5. Stock Service

### Responsibilities

- stock catalog lookup
- featured stock selection
- search endpoint
- featured-stock SSE stream
- market status REST + SSE
- stock insights and metrics
- fresh quote gRPC server
- scheduled market data refresh jobs

### External REST shape

- `GET /stocks`
- `GET /stocks/featured`
- `GET /stocks/featured/health`
- `POST /stocks/featured/refresh-once`
- `GET /stocks/{id}`
- `GET /stocks/{id}/insights`
- `GET /stocks/symbol/{symbol}`
- `GET /stocks/search`
- `GET /stocks/stream/featured`
- `GET /stocks/market-status`
- `GET /stocks/stream/market-status`

### Internal role in checkout

- validates stock existence and resolves canonical quote data over gRPC

## 6. Order Service

### Responsibilities

- cart CRUD
- quote locking
- order persistence
- full checkout orchestration
- order history and pagination

### External REST shape

- `GET /cart`
- `POST /cart/items`
- `PUT /cart/items/{stockId}`
- `DELETE /cart/items/{stockId}`
- `DELETE /cart`
- `POST /cart/lock-quote`
- `POST /cart/complete-order`
- `GET /orders`
- `GET /orders/paged`

### Internal dependencies

- gRPC client to payment-service
- gRPC client to stock-service
- gRPC client to customer-service portfolio sync

### Checkout design

Order-service is the orchestrator. The frontend never calls payment-service or portfolio-sync gRPC directly.

## 7. Payment Service

### Responsibilities

- get or create wallet
- deposit funds
- withdraw funds
- deduct funds for a completed purchase
- read wallet transaction history
- record payment state
- serve order payment gRPC endpoint

### External REST shape

- `GET /wallet/me`
- `POST /wallet/deposit`
- `POST /wallet/withdraw`
- `GET /wallet/transactions`
- `GET /wallet/transactions/paged`

### Recent hardening notes

- wallet history reads no longer rely on read-only transactions that may create wallets
- wallet error responses are now standardized through a global exception handler

## 8. Analytics Service

### Responsibilities today

- Kafka listener on topic `customer`
- protobuf event deserialization
- event logging

This service is currently a foundation for future analytics workloads rather than a full analytics pipeline.

## 9. Shared backend conventions

Across services, the current codebase follows these conventions:

- Spring Boot service per bounded context
- JPA/Hibernate per service-owned database
- gateway-first frontend access
- gRPC for low-latency internal checkout orchestration
- SSE for user-facing live market data
- PowerShell and Docker Compose for local orchestration

## 10. Backend gaps to plan next

Recommended next improvements before a public production rollout:

- actuator endpoints and metrics standardization
- centralized log correlation and request tracing
- database migrations via Flyway or Liquibase
- better test coverage for gateway-to-service auth propagation
- stricter rate limiting and edge protections
- resilience patterns for downstream service failures

