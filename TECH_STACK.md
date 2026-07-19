# TradePulse: Complete Technology Stack & Implementation Details

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    React + TypeScript Frontend                   │
│                      (Vite SPA with Routing)                     │
└──────────────────────┬──────────────────────────────────────────┘
                       │ HTTP/SSE
┌──────────────────────▼──────────────────────────────────────────┐
│              Spring Cloud API Gateway (4004)                     │
│         (JWT Validation + Header Injection + Routing)            │
└──────┬──────────┬──────────┬──────────┬──────────┬───────────────┘
       │          │          │          │          │
       ▼          ▼          ▼          ▼          ▼
    Auth      Customer    Stock      Order    Payment
   (4005)     (4000)     (4003)     (4006)     (4001)
     │          │          │          │          │
     ▼          ▼          ▼          ▼          ▼
  Auth-DB    Cust-DB    Stock-DB   Order-DB  Payment-DB
  (5000)     (5001)     (5002)     (5003)    (5004)
     └────────────────────────────────────────────────┘
                PostgreSQL Containers

    ┌─────────────────────────────────────────┐
    │        Apache Kafka (Broker)            │
    │  (Event Streaming & Persistence)        │
    └─────────────────────────────────────────┘
           ▲
           │ Kafka Events (Protobuf)
           ▼
    ┌─────────────────────────────────────────┐
    │      Analytics Service (4002)           │
    │   (Customer Events Consumer)            │
    └─────────────────────────────────────────┘

External Integrations:
┌─────────────────────────────────────────────────────────────────┐
│  Polygon.io / Massive API  │  SMTP Mail Service  │  gRPC APIs   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🖥️ Backend Technology Stack

### Language & Runtime
- **Java 21** - Latest LTS version with virtual threads support
- **Maven** - Dependency management and build automation with wrapper support

### Framework & Web
- **Spring Boot 3.x** - Microservices foundation
  - `spring-boot-starter-web` - REST API controllers
  - `spring-boot-starter-security` - JWT authentication
  - `spring-boot-starter-data-jpa` - Object-relational mapping
  - `spring-boot-starter-kafka` - Event streaming

### API Gateway & Routing
- **Spring Cloud Gateway** - Gateway service for routing and filtering
  - JWT validation interceptor
  - Header injection for authenticated user ID
  - Rate limiting ready
  - OpenAPI documentation aggregation

### Authentication & Security
- **Spring Security** - Authentication framework
  - JWT token generation and validation
  - Bearer token parsing from Authorization header
  - Role-based access control (RBAC)
  - Password encryption with BCrypt

### Data Access & Persistence
- **Hibernate/JPA** - ORM framework
  - Entity mappings with custom column definitions
  - Cascade operations for relationship management
  - Lazy loading for performance
  - Transaction management

- **PostgreSQL 15** - Relational database
  - 5 separate database containers (database-per-service)
  - ACID transactions at service boundary
  - Unique constraints for data integrity
  - Composite indexes for user-scoped queries
  - Connection pooling (HikariCP)

### Service-to-Service Communication
- **gRPC** - High-performance RPC framework
  - Protocol Buffers for message serialization
  - Services: `OrderPaymentService`, `StockQuoteService`, `PortfolioSyncService`
  - Synchronous orchestration for checkout flow

- **REST** - HTTP-based service calls
  - Customer service REST client to auth-service
  - Stock service REST client for dependency injection

### Event Streaming & Messaging
- **Apache Kafka 3.x** - Distributed event broker
  - Protobuf message serialization
  - Topic: `customer` for customer lifecycle events
  - Consumer groups for analytics processing
  - Persistence with local Docker volume

- **Protocol Buffers** - Data serialization format
  - `customer_event.proto` for Kafka events
  - gRPC service definitions (.proto files)
  - Type-safe message contracts

### Caching & Performance
- **Spring Cache Abstraction**
  - `@Cacheable` for method-level caching
  - Featured stocks persistent cache
  - Market status in-memory cache
  - Custom cache invalidation strategies

### Monitoring & Logging
- **SLF4J + Logback** - Logging framework
  - Structured logging across all services
  - Log levels for debugging and troubleshooting
  - Service health check endpoints

### Testing
- **JUnit 5** - Unit testing framework
- **Mockito** - Mocking library for unit tests
- **Spring Boot Test** - Integration testing support

---

## 🎨 Frontend Technology Stack

### Language & Typing
- **TypeScript 5.x** - Type-safe JavaScript
  - Strict mode enabled
  - Interface-based component props
  - Type definitions for all API responses

- **React 18.x** - UI library
  - Functional components with hooks
  - Context API for global state
  - Suspense for async operations

### Build & Development
- **Vite** - Next-generation build tool
  - Lightning-fast development server (HMR)
  - Optimized production builds
  - ES6 module support
  - Tree-shaking for smaller bundles

- **npm/Node.js** - Package management
  - Workspace configuration ready
  - npm scripts for dev/build/preview

### Routing & Navigation
- **React Router v6** - Client-side routing
  - Protected route guards
  - Nested route configurations
  - useParams, useNavigate, useLocation hooks
  - Layout routes for shared UI

### State Management
- **Context API** - Built-in React state management
  - `CartProvider` for shopping cart state
  - `OrdersProvider` for order history
  - `WatchlistProvider` for user watchlist
  - `WalletProvider` for wallet balance
  - `MarketStatusProvider` for global market state

### HTTP Client & API Communication
- **Axios** - Promise-based HTTP client
  - Request/response interceptors
  - Timeout configuration (15 seconds)
  - Bearer token automation
  - Global 401 error handling
  - Base URL routing to gateway

### Real-Time Communication
- **EventSource API** - Server-Sent Events (SSE)
  - Featured stocks live streaming
  - Market status live updates
  - Automatic reconnection handling
  - Graceful fallback to polling

### Styling & UI
- **TailwindCSS** - Utility-first CSS framework
  - Responsive design utilities
  - Dark mode support
  - Custom component styling
  - PurgeCSS for production optimization

- **CSS Modules** - Scoped styling
  - Component-specific styles
  - No global namespace pollution

### Development Tools
- **ESLint** - Code quality and linting
  - TypeScript support
  - React best practices
  - Code style enforcement

- **Prettier** - Code formatting
  - Consistent code style
  - Auto-formatting on save

---

## 📦 Key Dependencies & Libraries

### Backend Core
```
spring-boot-starter-web
spring-boot-starter-security
spring-boot-starter-data-jpa
spring-boot-starter-kafka
spring-cloud-starter-gateway
spring-boot-starter-actuator
jakarta.persistence (JPA)
hibernate-core
postgresql (JDBC driver)
protobuf-java (gRPC)
grpc-stub / grpc-netty
```

### Frontend Core
```
react@18
react-dom@18
react-router-dom@6
axios
typescript
vite
tailwindcss
```

---

## 🗄️ Database Schema Design

### Service Ownership Model
```
Auth Service DB:
├── users (authentication identity)
│   ├── user_id (PK)
│   ├── email (unique)
│   ├── password_hash
│   └── role

Customer Service DB:
├── customer (user profiles)
│   ├── user_id (PK)
│   ├── first_name, last_name
│   └── address fields
├── watchlist_items (stock watchlists)
│   └── embedded_id: user_id + stock_id
└── portfolio_holdings (current positions)
    ├── user_id, stock_id
    ├── total_quantity
    └── avg_buy_price

Stock Service DB:
├── stocks (catalog)
│   ├── stock_id (PK)
│   ├── ticker, name, exchange
│   └── featured, sort_order
├── stock_daily_ohlc (historical data)
│   ├── stock_id, trading_date
│   ├── open, high, low, close, volume
│   └── technical indicators (SMA, volatility)
├── stock_metrics (precomputed analytics)
│   ├── 52-week high/low
│   ├── Sharpe/Sortino ratios
│   └── RSI, MACD, technical indicators
└── cache tables (featured_stock_cache)

Order Service DB:
├── cart_items (shopping cart)
│   └── embedded_id: user_id + stock_id
└── orders (completed orders)
    ├── order_id (UUID PK)
    ├── user_id
    ├── status
    └── total

Payment Service DB:
├── wallets (user accounts)
│   ├── wallet_id (PK)
│   ├── user_id (unique)
│   └── balance
├── wallet_transactions (transaction ledger)
│   ├── transaction_id (PK)
│   ├── type (deposit/withdrawal/purchase)
│   └── balance_after
└── payments (order payments)
    ├── payment_id (PK)
    ├── order_id
    └── status
```

### Indexing Strategy
```
User-Scoped Queries:
- idx_cart_items_user_id
- idx_watchlist_items_user_id
- idx_portfolio_holdings_user_id
- idx_order_user_id
- idx_wallet_transaction_wallet_id

Operational Queries:
- idx_order_status
- idx_payment_status
- idx_payment_order_id
- idx_wallet_transaction_created_at

Featured/Performance:
- idx_stock_active
- idx_stock_featured_sort
- idx_stock_daily_ohlc_stock_date (unique)
```

---

## 🔌 API Endpoints Summary

### Authentication Routes (20+ endpoints)
```
POST /auth/login
POST /auth/register
GET /auth/validate
POST /auth/forgot-password/request-code
POST /auth/forgot-password/verify-code
POST /auth/forgot-password/reset
GET /auth/users/{userId}
PUT /auth/users/{userId}
DELETE /auth/users/{userId}
```

### Customer Routes
```
POST /api/customers/register
GET /api/customers/user/{userId}
PUT /api/customers/{userId}
DELETE /api/customers/{userId}
```

### Stock Routes
```
GET /api/stocks
GET /api/stocks/featured
GET /api/stocks/{id}
GET /api/stocks/{id}/insights
GET /api/stocks/search?query=
GET /api/stocks/market-status
GET /api/stocks/stream/featured (SSE)
GET /api/stocks/stream/market-status (SSE)
```

### Cart & Order Routes
```
GET /api/cart
POST /api/cart/items
PUT /api/cart/items/{stockId}
DELETE /api/cart/items/{stockId}
POST /api/cart/lock-quote
POST /api/cart/complete-order
GET /api/orders
GET /api/orders/paged
```

### Wallet Routes
```
GET /api/wallet/me
POST /api/wallet/deposit
POST /api/wallet/withdraw
GET /api/wallet/transactions
GET /api/wallet/transactions/paged
```

### Watchlist Routes
```
GET /api/watchlist
POST /api/watchlist/items
DELETE /api/watchlist/items/{stockId}
```

### Portfolio Routes
```
GET /api/portfolio
POST /api/portfolio/sell/{stockId}
```

---

## 🔐 Security Implementation

### Authentication
- JWT (JSON Web Tokens) with configurable expiration
- Bearer token in Authorization header
- Token validation at gateway entry point
- Automatic 401 response on invalid tokens

### Authorization
- User identity validation at gateway
- Header injection with validated `X-User-Id`
- User-scoped data access with database queries
- Role-based access control foundation

### Data Protection
- Password hashing with BCrypt
- Email uniqueness enforcement
- Session tokens with expiration
- Secure token storage (sessionStorage/localStorage)

### Cross-Service Security
- gRPC with plaintext for local development (TLS ready)
- Service-to-service authentication via shared secrets
- No cross-service foreign keys (API-based integrity)

---

## 🚀 Performance Features

### Backend Optimization
- Connection pooling (HikariCP) for database
- User-scoped database indexes for hot queries
- Lazy loading in JPA relationships
- Cache invalidation with timestamp validation
- gRPC for low-latency service calls
- Kafka batch processing for events

### Frontend Optimization
- Code splitting via Vite
- Tree-shaking for smaller bundles
- Lazy component loading with React.lazy()
- SSE instead of polling
- LocalStorage caching for featured stocks
- Stale-response guards for search operations

### Caching Layers
1. **Frontend LocalStorage** - Featured stocks, user preferences
2. **Frontend Memory** - Current page state, context values
3. **Backend In-Memory** - Market status, featured cache
4. **Database** - Persistent cache tables
5. **External API** - Massive WebSocket with local cache

---

## 🌐 External Integrations

### Market Data
- **Polygon.io / Massive API**
  - WebSocket for real-time quotes
  - Daily OHLC historical data
  - Technical indicators
  - Market status updates

### Communication
- **SMTP** for email notifications
  - Registration confirmation
  - Forgot password emails
  - Order confirmation (future)
  - Transaction notifications (future)

### Internal RPC
- **gRPC Services** for service orchestration
  - OrderPaymentService - Payment processing
  - StockQuoteService - Fresh quote validation
  - PortfolioSyncService - Holdings synchronization

---

## 🐳 Docker & Containerization

### Container Services
```
api-gateway:4004 (Spring Cloud Gateway)
auth-service:4005 (Auth microservice)
cust-service:4000 (Customer microservice)
stock-service:4003 (Stock microservice)
order-service:4006 (Order microservice)
payment-service:4001 (Payment microservice)
analytics-service:4002 (Analytics microservice)
portfolio-service (Portfolio microservice)

auth-service-db:5000 (PostgreSQL)
cust-service-db:5001 (PostgreSQL)
stock-service-db:5002 (PostgreSQL)
order-service-db:5003 (PostgreSQL)
payment-service-db:5004 (PostgreSQL)

kafka:9092 (Kafka Broker)
```

### Docker Compose Configuration
- Environment-based service discovery
- Volume persistence for databases and Kafka
- Health checks for service readiness
- Network isolation between services
- Custom networking for inter-service communication

---

## 🔄 Development Workflow

### Local Development
```
Backend:
1. Docker Compose stack with all services
2. Maven-based builds per service
3. Spring Boot DevTools for hot reload
4. Database migrations on startup (ddl-auto=update)

Frontend:
1. Vite dev server with HMR
2. Axios proxy to gateway (4004)
3. TypeScript compilation on save
4. ESLint/Prettier integration
```

### Deployment Ready
- Docker images per service
- Environment-based configuration
- Database migration support
- Health check endpoints
- Structured logging for debugging
- OpenAPI documentation per service

---

## 📊 Metrics & Observability

### Available Observability
- Spring Actuator endpoints for health checks
- SLF4J structured logging
- Service logs available in Docker containers
- Database query logging capabilities
- Request/response logging in HTTP layers

### Recommended Enhancements
- Prometheus for metrics collection
- Grafana for dashboards
- Jaeger for distributed tracing
- ELK stack for log aggregation
- Custom metrics for business events

---

## 🎯 Design Patterns Employed

### Architectural Patterns
- Microservices Architecture
- API Gateway Pattern
- Database-per-Service
- CQRS (Query vs Command separation)
- Event Sourcing (in transactions ledger)
- Saga Pattern (distributed transactions)

### Cloud-Native Patterns
- Circuit Breaker ready (with Spring Retry)
- Service Registry ready (Consul/Eureka compatible)
- Load Balancer ready (stateless services)
- Horizontal Scaling ready
- 12-Factor App compliant

### Development Patterns
- Dependency Injection (Spring)
- Repository Pattern (Data Access)
- DTO Pattern (Data Transfer)
- Mapper Pattern (Entity conversion)
- Provider Pattern (State management)
- Hook Pattern (React)

---

## ✅ Quality Assurance

### Code Quality
- TypeScript strict mode for frontend
- Maven checkstyle for backend
- ESLint for JavaScript standards
- Automated formatting with Prettier
- JDBC tests for database operations

### Reliability
- Error handling across all layers
- Graceful degradation for external APIs
- Compensation transactions for failures
- Health check endpoints
- Structured error responses

---

## 📝 Stack Summary

This technology stack covers the full application surface:
- **Modern Backend**: Spring Boot 3.x, Microservices, gRPC, Kafka
- **Modern Frontend**: React 18, TypeScript, Vite, Context API
- **Data Management**: PostgreSQL, JPA/Hibernate, strategic indexing
- **Scalability**: Stateless services, async messaging, multi-layer caching
- **Production-Ready**: Security hardening, error handling, monitoring foundations
- **DevOps**: Docker, environment configuration, health checks

This is an industry-grade application built with production standards, not a tutorial project.


