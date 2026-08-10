# Saga and Consistency Model

This document explains how TradePulse handles cross-service consistency without distributed transactions.

## 1. Why saga-style coordination is needed

TradePulse uses database-per-service ownership. Because each service writes only to its own database, there is no shared ACID transaction across services.

Therefore, multi-step workflows use orchestration and compensation patterns.

## 2. Registration saga (Auth user + Customer profile)

Primary orchestrator:
- `tradepulse-backend/customer-service/src/main/java/com/tradepulse/customerService/service/CustomerService.java`

Entry endpoint:
- `POST /api/customers/register`

Flow:
1. customer-service receives registration payload.
2. customer-service calls auth-service (`/register`) to create auth identity.
3. if auth creation succeeds, customer-service persists profile in `customer` table.
4. customer-service publishes Kafka customer event.
5. response returns combined registration success.

Compensation logic:
- if customer save fails after auth user was created, customer-service calls auth-service delete (`/users/{userId}`) to roll back the auth identity.
- this compensation is implemented in `registerCustomer(...)` using `rollbackAuthUser(...)`.

Client used for auth interactions:
- `tradepulse-backend/customer-service/src/main/java/com/tradepulse/customerService/client/AuthServiceClient.java`

## 3. Checkout orchestration (payment + order + portfolio eventing)

Primary orchestrator:
- `tradepulse-backend/order-service/src/main/java/com/tradepulse/orderservice/service/CartService.java`

Entry endpoints:
- `POST /api/cart/lock-quote`
- `POST /api/cart/complete-order`

Runtime flow:
1. frontend requests quote lock (fresh canonical prices).
2. order-service validates stock and resolves quotes through stock gRPC.
3. order-service calls payment-service gRPC to complete payment.
4. if payment succeeds, order-service persists the order and outbox records in the same DB transaction.
5. cart is cleared and success response returned.
6. scheduled outbox relay publishes `ORDER_COMPLETED` to Kafka after commit.
7. portfolio-service consumes the order event and updates holdings asynchronously.

Design intent:
- order-service is the single orchestration boundary for checkout.
- frontend does not call payment or portfolio services directly.

## 4. Consistency boundaries by domain

- auth-service: user credentials and identity
- customer-service: customer profile, watchlist, portfolio state
- customer-service: customer profile and watchlist
- portfolio-service: portfolio holdings, transactions, and sell-side settlement orchestration
- payment-service: wallet and payment ledger
- order-service: cart/order lifecycle
- stock-service: market data, quotes, insights

Logical keys across services:
- `user_id` links user-owned domain records
- `stock_id` links stock-owned domain records

## 5. Failure handling examples

### Registration failure after auth created
- customer insert fails
- compensation triggers auth delete
- caller receives error, avoiding orphan auth user

### Payment failure during order completion
- order-service throws payment failure
- checkout response fails and cart/order progression stops
- no outbox order event is written when payment is not completed

### Portfolio update failure after payment
- payment and order may already be completed
- `ORDER_COMPLETED` remains durable in Kafka / consumer retry flow
- portfolio-service retries processing; on repeated failure, the record can be sent to DLQ depending on listener error handling

## 6. Current strengths

- explicit orchestration boundaries are clear in code
- registration compensation prevents common split-write inconsistency
- checkout service order is deterministic (quote -> payment -> order + outbox -> Kafka consumer update)
- gateway-enforced identity propagation supports correct ownership scoping

## 7. Current limitations and planned hardening

Recommended next steps for stronger production guarantees:

1. Add end-to-end idempotency keys for complete-order and payment completion.
2. Add stronger saga status/audit visibility for support teams.
3. Expand integration tests for partial-failure and replay scenarios.
4. Monitor Kafka lag / DLQ volume as part of operational readiness.
5. Add replay tooling for failed portfolio event recovery.

## 8. Design philosophy summary

TradePulse deliberately avoids distributed two-phase commit and instead uses practical microservice consistency:
- orchestrated workflow steps
- compensation where possible
- clear ownership per service

This is a realistic pattern used in production microservice systems where availability and service autonomy are prioritized over strong global consistency.

