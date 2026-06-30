# Saga and Consistency Model

This document explains how TradePulseAI handles cross-service consistency without distributed transactions.

## 1. Why saga-style coordination is needed

TradePulseAI uses database-per-service ownership. Because each service writes only to its own database, there is no shared ACID transaction across services.

Therefore, multi-step workflows use orchestration and compensation patterns.

## 2. Registration saga (Auth user + Customer profile)

Primary orchestrator:
- `tradepulseai-backend/cust-service/src/main/java/com/tradepulseai/custservice/service/CustomerService.java`

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
- `tradepulseai-backend/cust-service/src/main/java/com/tradepulseai/custservice/client/AuthServiceClient.java`

## 3. Checkout orchestration (order + payment + portfolio sync)

Primary orchestrator:
- `tradepulseai-backend/order-service/src/main/java/com/tradepulseai/orderservice/service/CartService.java`

Entry endpoints:
- `POST /api/cart/lock-quote`
- `POST /api/cart/complete-order`

Runtime flow:
1. frontend requests quote lock (fresh canonical prices).
2. order-service validates stock and resolves quotes through stock gRPC.
3. order-service saves order.
4. order-service calls payment-service gRPC to complete payment.
5. on payment success, order-service calls customer-service portfolio-sync gRPC.
6. cart is cleared and success response returned.

Design intent:
- order-service is the single orchestration boundary for checkout.
- frontend does not call payment or portfolio sync services directly.

## 4. Consistency boundaries by domain

- auth-service: user credentials and identity
- cust-service: customer profile, watchlist, portfolio state
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
- portfolio sync does not proceed when payment is not completed

### Portfolio sync failure after payment
- payment may already be completed
- order-service logs and propagates error from sync path
- this is the main candidate for future retry/outbox enhancement

## 6. Current strengths

- explicit orchestration boundaries are clear in code
- registration compensation prevents common split-write inconsistency
- checkout service order is deterministic (quote -> order -> payment -> portfolio sync)
- gateway-enforced identity propagation supports correct ownership scoping

## 7. Current limitations and planned hardening

Recommended next steps for stronger production guarantees:

1. Introduce outbox/inbox pattern for critical cross-service events.
2. Add idempotency keys for complete-order and payment completion.
3. Add retry policies with dead-letter handling for portfolio-sync failures.
4. Add saga status audit table for support/debug visibility.
5. Expand integration tests for partial-failure scenarios.

## 8. Recruiter / hiring manager summary

TradePulseAI deliberately avoids distributed two-phase commit and instead uses practical microservice consistency:
- orchestrated workflow steps
- compensation where possible
- clear ownership per service

This is a realistic pattern used in production microservice systems where availability and service autonomy are prioritized.

