# Backend Services

This document summarizes the live responsibilities of each backend service in the current architecture.

## auth-service

### Main responsibilities

- user authentication and JWT issuance
- credential validation
- forgot-password / reset-password flow
- user lookup and lifecycle operations used by other services

## cust-service

### Main responsibilities

- customer profile CRUD
- customer registration saga with auth-service
- watchlist management
- Kafka publishing of customer events

### External REST shape

- `/customers/register`
- `/customers/user/{userId}`
- `/customers/{userId}`
- `/watchlist`
- `/watchlist/items`

### Internal dependencies

- REST client to auth-service
- Kafka producer for customer events

## portfolio-service

### Main responsibilities

- portfolio holdings read model
- portfolio transaction history
- sell operations
- portfolio sync gRPC server for completed orders
- REST client to stock-service for quote/session reads in portfolio views

### External REST shape

- `/portfolio`
- `/portfolio/sell/{stockId}`

### Internal dependencies

- REST client to stock-service
- gRPC server for portfolio synchronization

## stock-service

### Main responsibilities

- stock catalog and market data
- featured/search endpoints
- market status
- news/sentiment enrichment
- ML-facing stock data source
- stock quote gRPC server for checkout validation

## order-service

### Main responsibilities

- cart lifecycle
- order completion orchestration
- payment gRPC call
- portfolio sync gRPC call
- compensation flow on portfolio sync failure

## payment-service

### Main responsibilities

- wallet balance management
- wallet transactions ledger
- payment processing
- payment/refund compensation via gRPC

## api-gateway

### Main responsibilities

- route frontend traffic to downstream services
- JWT validation and injection of validated `X-User-Id`
- rate limiting and docs aggregation

## ml-service

### Main responsibilities

- model training and retraining
- prediction endpoints / internal ML APIs
- reads from stock-service database
