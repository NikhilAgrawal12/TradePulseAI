# TradePulse

TradePulse is a production-grade stock trading simulation platform built with Spring Boot microservices, React + TypeScript, and a comprehensive infrastructure stack. It covers authentication, stock market data, watchlists, cart, wallet, checkout, order history, and portfolio management.

---

## What Makes This Project Interesting

- **7 independent Spring Boot microservices** with clear domain ownership
- **Real-time market data** streamed from Massive WebSocket API → SSE → UI
- **Distributed transaction orchestration** using the Saga pattern (no 2PC)
- **API Gateway security** — JWT validated at the edge, trusted `X-User-Id` injected for all downstream services
- **Kafka + Protobuf** for asynchronous customer lifecycle events
- **gRPC** for synchronous service-to-service calls during checkout

---

## Repository Layout

```
TradePulse/
├── README.md
├── QUICK_START.md
├── ARCHITECTURE.md
├── API_SURFACE.md
├── DATABASE_DESIGN.md
├── BACKEND_SERVICES.md
├── FRONTEND_ARCHITECTURE.md
├── DATA_FLOW_MASSIVE_TO_FRONTEND.md
├── SAGA_AND_CONSISTENCY.md
├── TECH_STACK.md
├── SCALABILITY_AND_PERFORMANCE.md
├── PROJECT_HIGHLIGHTS.md
├── DOCUMENTATION_INDEX.md
├── OPERATIONS_RUNBOOK.md
│
├── tradepulse-backend/
│   ├── api-gateway (4004)
│   ├── auth-service (4005)
│   ├── cust-service (4000)
│   ├── stock-service (4003)
│   ├── order-service (4006)
│   ├── payment-service (4001)
│   ├── portfolio-service
│   ├── analytics-service (4002)
│   ├── ml-service
│   └── docker-compose.persistent.yml
│
└── tradepulse-frontend/
    ├── src/
    │   ├── pages/ (12 routes)
    │   ├── components/
    │   ├── context/ (5 providers)
    │   ├── utils/
    │   └── types/
    ├── vite.config.ts
    └── package.json
```

---

## Core Features

### User Management
- Registration with coordinated auth + customer profile creation (saga with compensation)
- Login with stateless JWT-based session management
- Forgot password with email verification flow
- Full customer profile CRUD with address data
- Account deletion with cascade cleanup

### Trading & Market Data
- 5000+ stocks from Polygon.io / Massive API
- Live featured stock feed via Server-Sent Events
- Real-time stock search with SSE-backed live filtering
- Market status tracking (open/closed) with 60-second freshness enforcement
- Stock insights with historical OHLC, SMA (20/50/200), volatility, RSI, MACD, Sharpe/Sortino

### Cart & Orders
- Cart management (add, update quantity, remove, clear)
- Price locking before checkout with fresh quote validation via gRPC
- Order completion orchestrated through payment and portfolio sync
- Paginated order history

### Wallet & Payments
- Wallet with deposit and withdrawal
- Immutable transaction ledger (balance + balance_after per entry)
- Purchase deduction on order completion via gRPC
- Compensation/refund on portfolio sync failure

### Portfolio
- Holdings per user/stock with average buy price
- Buy/sell transaction history
- Realized/unrealized PnL
- Market-session-aware sell operations
- Portfolio sync after successful checkout

### Analytics
- Customer lifecycle events published to Kafka (Protobuf)
- Analytics service consuming and processing events

---

## Quick Start

```bash
# Backend — Terminal 1
cd tradepulse-backend/scripts
.\up-persistent.ps1

# Frontend — Terminal 2
cd tradepulse-frontend
npm install
npm run dev

# Open http://localhost:5173
```

Full setup details in `QUICK_START.md`.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 21, Spring Boot 3.x, Spring Security, Spring Cloud Gateway |
| **Data Access** | Spring Data JPA, Hibernate, PostgreSQL |
| **Messaging** | Apache Kafka, Protobuf |
| **RPC** | gRPC with Protocol Buffers |
| **Frontend** | React 18, TypeScript, Vite, React Router, Axios |
| **State** | React Context API (Cart, Wallet, Orders, Watchlist, MarketStatus) |
| **Real-Time** | Server-Sent Events (SSE), Massive WebSocket API |
| **Infra** | Docker Compose, 5 PostgreSQL containers, Kafka broker |

See `TECH_STACK.md` for the full breakdown.

---

## Architecture Summary

```
React Frontend
    │ HTTP / SSE
    ▼
API Gateway (4004) — JWT validation, X-User-Id injection
    │
    ├── Auth Service (4005)      ← users, JWT
    ├── Customer Service (4000)  ← profiles, watchlists
    ├── Stock Service (4003)     ← catalog, SSE, insights
    ├── Order Service (4006)     ← cart, orders, orchestration
    └── Payment Service (4001)  ← wallets, transactions

Order Service gRPC:
    → Payment Service (9002)    ← complete payment
    → Stock Service (9003)      ← fresh quote
    → Portfolio Service (9004)  ← sync holdings

Customer Service → Kafka → Analytics Service
```

See `ARCHITECTURE.md` for full diagrams and flow descriptions.

---

## Security Model

- JWT validated at the API Gateway (not per downstream service)
- Client-supplied `X-User-Id` is stripped and replaced with a gateway-validated value
- User-scoped data access enforced at query level
- Password hashing with BCrypt
- Email uniqueness enforced at database level

---

## Documentation Set

| File | What it covers |
|------|----------------|
| `README.md` | Project overview and navigation |
| `QUICK_START.md` | Prerequisites, setup, first-run checklist |
| `ARCHITECTURE.md` | System topology, service responsibilities, request flows |
| `API_SURFACE.md` | REST routes, auth model, SSE and gRPC contracts |
| `DATABASE_DESIGN.md` | Schemas, indexes, cross-service identity model |
| `BACKEND_SERVICES.md` | Per-service responsibilities and dependencies |
| `FRONTEND_ARCHITECTURE.md` | React patterns, routes, state providers, SSE usage |
| `DATA_FLOW_MASSIVE_TO_FRONTEND.md` | Market data path from Massive API → SSE → UI |
| `SAGA_AND_CONSISTENCY.md` | Registration saga, checkout orchestration, compensation |
| `TECH_STACK.md` | Full technology inventory with rationale |
| `SCALABILITY_AND_PERFORMANCE.md` | Caching layers, horizontal scaling, DB replication, Kubernetes readiness |
| `PROJECT_HIGHLIGHTS.md` | Skills, patterns, and feature completeness overview |
| `OPERATIONS_RUNBOOK.md` | Env vars, deployment, health checks, troubleshooting |
| `DOCUMENTATION_INDEX.md` | Reading paths by role and topic cross-references |

---

## Design Decisions

### Why database-per-service?
Each service owns its schema, scales independently, and evolves its data model without coordinating with other teams. No shared database means no accidental cross-service coupling.

### Why Saga pattern instead of distributed transactions?
Two-phase commit sacrifices availability and tightly couples services. Sagas use explicit orchestration steps with compensation logic — each service handles its own database and failure is handled at the workflow level.

### Why SSE for market data?
SSE is simpler than WebSockets for one-way server push, works natively with HTTP/2 and load balancers, and the browser EventSource API handles reconnection automatically.

### Why gRPC for internal calls?
gRPC provides typed contracts via Protocol Buffers, lower latency than REST for synchronous service calls, and explicit failure semantics needed during checkout orchestration.

---

## Project Statistics

| Metric | Value |
|--------|-------|
| Backend services | 7 |
| PostgreSQL databases | 5 |
| REST endpoints | 40+ |
| Frontend routes | 12 |
| Context providers | 5 |
| gRPC services | 3 |
| Kafka topics | 1+ |

---

## Future Enhancements

- Machine learning stock recommendations (ml-service foundation in place)
- Advanced observability: Prometheus, Grafana, distributed tracing
- Secrets management: HashiCorp Vault
- Outbox pattern for at-least-once event delivery guarantees
- End-to-end integration tests
- GraphQL API layer
- Mobile app (React Native)

---

## License

No project-wide license defined. Add one before external distribution.
