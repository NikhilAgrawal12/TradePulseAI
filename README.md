# TradePulseAI

TradePulseAI is a stock-trading simulation platform with a React frontend and a Spring Boot microservices backend. It supports authentication, customer onboarding, watchlists, carts, wallet funding, checkout, orders, portfolio tracking, stock analytics, live featured-stock streaming, and market-session awareness.

## What is in this repository

- `tradepulseai-frontend/` — Vite + React + TypeScript web client
- `tradepulseai-backend/` — Spring Boot microservices, PostgreSQL, Kafka, gRPC, API gateway
- `tradepulseai-backend/docker-compose.persistent.yml` — main local container stack
- `tradepulseai-backend/scripts/` — PowerShell helpers for the persistent backend stack

## Core capabilities

- User registration and login with JWT-based authentication
- Customer profile storage and account management
- Live featured stock feed over SSE
- Backend-cached market-status feed with 60-second freshness rules
- Watchlist and cart management
- Wallet deposit, withdrawal, and purchase deduction flows
- Order completion with fresh quote validation and portfolio sync
- Portfolio holdings, transactions, realized/unrealized PnL
- Stock insight pages backed by historical OHLC and derived metrics
- Kafka customer events for analytics consumption

## Current architecture snapshot

- **Frontend** talks only to the **API Gateway** using `/api/**` and `/auth/**`
- **API Gateway** validates JWTs with `auth-service` and injects the trusted `X-User-Id` header
- **order-service** orchestrates checkout via gRPC to `payment-service`, `stock-service`, and `cust-service`
- **stock-service** owns stock catalog, featured stocks, SSE streams, market session cache, and stock insights
- **cust-service** owns customers, watchlists, and portfolios
- **payment-service** owns wallets, wallet transactions, and payment records
- **analytics-service** currently consumes customer Kafka events

## Documentation map

This repository now uses a 10-document set based on the live code:

1. `README.md` — project overview and document map
2. `QUICK_START.md` — local setup and verification steps
3. `ARCHITECTURE.md` — system design, runtime flows, and integrations
4. `DATABASE_DESIGN.md` — storage ownership, tables, and indexing notes
5. `BACKEND_SERVICES.md` — backend service responsibilities and ports
6. `FRONTEND_ARCHITECTURE.md` — routes, contexts, state, and streaming behavior
7. `API_SURFACE.md` — gateway routes, auth model, SSE, and gRPC contracts
8. `OPERATIONS_RUNBOOK.md` — deployment, operations, env vars, and support procedures
9. `DATA_FLOW_MASSIVE_TO_FRONTEND.md` — exact market-data path from Massive -> stock-service -> gateway -> frontend
10. `SAGA_AND_CONSISTENCY.md` — registration saga, checkout orchestration, compensation, and consistency boundaries

## Recommended reading order

If you are new to the project, read in this order:

1. `README.md`
2. `QUICK_START.md`
3. `ARCHITECTURE.md`
4. `DATABASE_DESIGN.md`
5. `API_SURFACE.md`
6. `DATA_FLOW_MASSIVE_TO_FRONTEND.md`
7. `SAGA_AND_CONSISTENCY.md`

## Production-readiness notes

Recent hardening work reflected in the codebase includes:

- Market status is cached and shared at the app level instead of page-local state
- Market status freshness is enforced against a 60-second backend timestamp window
- API gateway now injects the validated user id instead of trusting the client-sent `X-User-Id`
- Customer profile reads/updates/deletes now require path-user-id and authenticated-user-id alignment
- Frontend HTTP handling now clears invalid sessions on `401` and syncs auth changes across tabs
- Wallet transaction reads no longer rely on read-only transactions that may create rows

## Current boundaries

This repository is close to an end-to-end application, but still has obvious next-phase work:

- machine-learning components are not implemented yet
- observability is basic and not yet standardized with metrics/tracing dashboards
- secrets management is environment-based and should move to a proper secret manager in hosted environments
- integration and end-to-end automated tests should be expanded before public release

## License and usage

No project-wide license file is defined in this repository snapshot. Add one before external distribution.

