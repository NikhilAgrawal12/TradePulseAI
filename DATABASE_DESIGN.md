# Database Design

TradePulse uses **database-per-service** ownership. Each main backend service persists to its own PostgreSQL database container in local Docker.

## 1. Data ownership model

| Service | Database Container | Owns |
|---|---|---|
| Auth Service | `auth-service-db` | users and credentials |
| Customer Service | `cust-service-db` | customer profiles, watchlists, portfolios |
| Order Service | `order-service-db` | carts, orders, order items |
| Payment Service | `payment-service-db` | wallets, wallet transactions, payments |
| Stock Service | `stock-service-db` | stock catalog, OHLC history, metrics, caches |

This separation is intentional and matches the service boundaries in code.

## 2. Auth Service schema

### `users`

Purpose:
- stores authentication identities
- source of truth for email, password hash, and role

Key columns:
- `user_id` (PK)
- `email` (unique)
- `password`
- `role`

Notes:
- email uniqueness is enforced at the database level
- JWT `userId` claim is derived from `user_id`

## 3. Customer Service schema

### `customer`

Purpose:
- stores user profile and address information

Key columns:
- `user_id` (PK)
- `first_name`
- `last_name`
- `phone_number`
- `address_line1`
- `address_line2`
- `city`
- `state`
- `postal_code`
- `country`
- `date_of_birth`
- `registration_date`

### `watchlist_items`

Purpose:
- stores user watchlist membership

Key structure:
- embedded id of `user_id + stock_id`
- `created_at`

Indexing present in code:
- `idx_watchlist_items_user_id`

### `portfolio_holdings`

Purpose:
- stores current owned quantity per user and stock

Key structure:
- embedded id of `user_id + stock_id`
- `total_quantity`
- `avg_buy_price`
- `created_at`
- `updated_at`

Indexing present in code:
- `idx_portfolio_holdings_user_id`

### `portfolio_transactions`

Purpose:
- append-only ledger of buy/sell activity used for history and PnL calculations

Key columns:
- `transaction_id` (PK)
- `user_id`
- `stock_id`
- `transaction_type`
- `price`
- `quantity`
- `executed_at`

Indexing present in code:
- `idx_portfolio_transaction_user_id`
- `idx_portfolio_transaction_stock_id`

## 4. Order Service schema

### `cart_items`

Purpose:
- current shopping cart state before checkout

Key structure:
- embedded id of `user_id + stock_id`
- `quantity`
- `created_at`
- `updated_at`

Indexing present in code:
- `idx_cart_items_user_id`

### `orders`

Purpose:
- completed orders

Key columns:
- `order_id` (UUID PK)
- `user_id`
- `order_number`
- `status`
- `subtotal`
- `total`
- `created_at`

Indexing present in code:
- `idx_order_user_id`
- `idx_order_status`

### `order_items`

Purpose:
- order line items for each completed order

Typical data stored:
- `order_id`
- `stock_id`
- `symbol`
- `price`
- `quantity`
- `line_total`

This table is linked from `TradeOrder` through `TradeOrderItem`.

## 5. Payment Service schema

### `wallets`

Purpose:
- one wallet per user

Key columns:
- `wallet_id` (PK)
- `user_id` (unique)
- `balance`
- `created_at`
- `updated_at`

### `wallet_transactions`

Purpose:
- immutable wallet ledger for deposits, withdrawals, and purchases

Key columns:
- `transaction_id` (PK)
- `wallet_id`
- `transaction_type`
- `amount`
- `balance_after`
- `created_at`

Indexing present in code:
- `idx_wallet_transaction_wallet_id`
- `idx_wallet_transaction_created_at`

### `payments`

Purpose:
- payment record linked to order completion

Key columns:
- `payment_id` (PK)
- `order_id`
- `total_amount`
- `status`
- `created_at`

Indexing present in code:
- `idx_payment_order_id`
- `idx_payment_status`

## 6. Stock Service schema

### `stocks`

Purpose:
- master stock catalog used by home, search, watchlist, cart, and portfolio screens

Key columns:
- `stock_id` (PK)
- `ticker`
- `name`
- `exchange_id`
- `market`
- `locale`
- `type`
- `active`
- `sic_code`
- `sic_description`
- `cik`
- `homepage_url`
- `list_date`
- `market_cap`
- `is_featured`
- `sort_order`
- `updated_at`

Indexing present in code:
- `idx_stock_active`
- `idx_stock_featured_sort`
- `idx_stock_exchange_id`

### `stock_daily_ohlc`

Purpose:
- historical daily price and volume data per stock

Key columns:
- `id` (PK)
- `stock_id`
- `trading_date`
- `open_price`
- `high_price`
- `low_price`
- `close_price`
- `volume`
- `vwap`
- `sma_20`
- `sma_50`
- `sma_200`
- `volatility_30d`
- `volatility_90d`
- `daily_return_percent`
- `is_otc`
- `adjusted`
- `updated_at`

Constraints and indexes:
- unique constraint on `stock_id + trading_date`
- index `idx_stock_daily_ohlc_stock_date`

### `stock_metrics`

Purpose:
- precomputed stock analytics for insight pages

Examples of stored fields:
- week/month/year returns
- 52-week high/low
- volatility
- volume ratios
- drawdown fields
- Sharpe / Sortino
- RSI / MACD / momentum
- golden cross / death cross

### Additional stock-service cache tables

The live codebase also contains persistent cache entities for:
- `featured_stock_cache`
- `all_stocks_last_value_cache`
- exchange data

These support fast startup and live market UX rather than being user-owned transactional data.

## 7. Cross-service identity model

There is no shared relational foreign-key graph across services.

Instead:
- `user_id` is the logical identity link between auth, customer, order, and payment domains
- `stock_id` is the logical stock link between stock, cart, order, watchlist, and portfolio domains
- integrity across services is enforced in application logic, not cross-database foreign keys

## 8. Indexing strategy summary

Already visible in entity mappings:

- user-scoped indexes on carts, watchlists, holdings, transactions, and orders
- order/payment status indexes for operational lookups
- stock featured-sort and active-state indexes for home page performance
- stock OHLC uniqueness and lookup index for chart/insight access patterns
- wallet transaction history indexes for descending history reads

## 9. Data design strengths

- service-owned persistence boundaries are clear
- hot user-path queries have explicit indexes in the JPA models
- historical stock data is separated from stock identity metadata
- portfolios use both current-state holdings and event-style transactions
- wallets use both current balance and transaction ledger

## 10. Next database improvements for a hosted production deployment

Recommended future upgrades:

- move from `ddl-auto=update` to controlled migrations
- add backup/restore automation per database
- add partitioning or archiving plans for high-growth history tables
- define retention policies for analytics/event data
- add database-level observability for slow queries and connection saturation

