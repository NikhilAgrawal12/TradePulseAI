# Database Design

TradePulse uses **database-per-service** ownership. Each main backend service persists to its own PostgreSQL database container in local Docker.

## 1. Data ownership model

| Service | Database Container | Owns |
|---|---|---|
| Auth Service | `auth-service-db` | users and credentials |
| Customer Service | `customer-service-db` | customer profiles and watchlists |
| Order Service | `order-service-db` | carts, orders, order items |
| Payment Service | `payment-service-db` | wallets, wallet transactions, payments |
| Portfolio Service | `portfolio-service-db` | portfolio holdings and portfolio transactions |
| Stock Service | `stock-service-db` | stock catalog + market/cache tables |
| Analytics Service | `analytics-service-db` | stocks replica, OHLC history, metrics snapshots, ML features, model registry |

This separation is intentional and matches the service boundaries in code.

## 2. Auth Service schema

### `users`

Purpose:
- stores authentication identities
- source of truth for email, password hash, and role

Columns:
- `user_id` BIGINT, PK, generated identity
- `email` VARCHAR(255), NOT NULL, UNIQUE
- `password` VARCHAR(255), NOT NULL
- `role` VARCHAR(50), NOT NULL

Constraints and indexes:
- primary key: `users_pkey` on (`user_id`)
- unique constraint: `users_email_key` on (`email`)

Notes:
- email uniqueness is enforced at the database level
- JWT `userId` claim is derived from `user_id`

## 3. Customer Service schema

### `customer`

Purpose:
- stores user profile and address information

Columns:
- `user_id` BIGINT, PK, NOT NULL
- `first_name` VARCHAR(100), NOT NULL
- `last_name` VARCHAR(100), NOT NULL
- `phone_number` VARCHAR(50), NOT NULL
- `address_line1` VARCHAR(255), NOT NULL
- `address_line2` VARCHAR(255), nullable
- `city` VARCHAR(100), NOT NULL
- `state` VARCHAR(100), NOT NULL
- `postal_code` VARCHAR(20), NOT NULL
- `country` VARCHAR(100), NOT NULL
- `date_of_birth` DATE, NOT NULL
- `registration_date` TIMESTAMP, NOT NULL

Constraints and indexes:
- primary key: `customer_pkey` on (`user_id`)

### `watchlist_items`

Purpose:
- stores user watchlist membership

Columns:
- `user_id` BIGINT, NOT NULL
- `stock_id` BIGINT, NOT NULL
- `created_at` TIMESTAMP, NOT NULL

Constraints and indexes:
- primary key: `pk_watchlist_items` on (`user_id`, `stock_id`)



## 4. Order Service schema

### `cart_items`

Purpose:
- current shopping cart state before checkout

Columns:
- `user_id` BIGINT, NOT NULL
- `stock_id` BIGINT, NOT NULL
- `quantity` NUMERIC, NOT NULL
- `created_at` TIMESTAMPTZ, NOT NULL
- `updated_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `cart_items_pkey` on (`stock_id`, `user_id`)
- index: `idx_cart_items_user_id` on (`user_id`)

### `orders`

Purpose:
- completed orders

Columns:
- `order_id` VARCHAR, PK
- `user_id` BIGINT, NOT NULL
- `order_number` INTEGER, nullable
- `status` VARCHAR, NOT NULL
- `total` NUMERIC, NOT NULL
- `created_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `orders_pkey` on (`order_id`)
- unique constraint: `uknthkiu7pgmnqnu86i2jyoe2v7` on (`order_number`)
- indexes: `idx_order_user_id`, `idx_order_status`, `idx_order_user_id_created_at`

### `order_items`

Purpose:
- order line items for each completed order

Columns:
- `order_id` VARCHAR, NOT NULL
- `stock_id` VARCHAR, NOT NULL
- `price` NUMERIC, NOT NULL
- `quantity` NUMERIC, NOT NULL

Constraints and indexes:
- primary key: `order_items_pkey` on (`order_id`, `stock_id`)
- foreign key: `fkbioxgbv59vetrxe0ejfubep1w` on (`order_id`) -> `orders(order_id)`
- index: `idx_order_items_order_id` on (`order_id`)

## 5. Payment Service schema

### `wallets`

Purpose:
- one wallet per user

Columns:
- `wallet_id` BIGINT, PK
- `user_id` BIGINT, NOT NULL, UNIQUE
- `balance` NUMERIC, NOT NULL
- `created_at` TIMESTAMP, NOT NULL
- `updated_at` TIMESTAMP, NOT NULL

Constraints and indexes:
- primary key: `wallets_pkey` on (`wallet_id`)
- unique constraint: `uksswfdl9fq40xlkove1y5kc7kv` on (`user_id`)

### `wallet_transactions`

Purpose:
- immutable wallet ledger for deposits, withdrawals, and purchases

Columns:
- `transaction_id` VARCHAR, PK
- `wallet_id` BIGINT, NOT NULL
- `transaction_type` VARCHAR, NOT NULL
- `amount` NUMERIC, NOT NULL
- `balance_after` NUMERIC, NOT NULL
- `created_at` TIMESTAMP, NOT NULL

Constraints and indexes:
- primary key: `wallet_transactions_pkey` on (`transaction_id`)
- indexes: `idx_wallet_transaction_wallet_id`, `idx_wallet_transaction_created_at`, `idx_wallet_transaction_wallet_id_created_at`

### `payments`

Purpose:
- payment record linked to order completion

Columns:
- `payment_id` BIGINT, PK
- `order_id` VARCHAR, NOT NULL
- `total_amount` NUMERIC, NOT NULL
- `status` VARCHAR, NOT NULL
- `created_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `payments_pkey` on (`payment_id`)
- indexes: `idx_payment_order_id`, `idx_payment_status`

## 6. Portfolio Service schema

### `portfolio_holdings`

Purpose:
- stores current owned quantity per user and stock

Columns:
- `user_id` BIGINT, NOT NULL
- `stock_id` BIGINT, NOT NULL
- `total_quantity` NUMERIC(18,2), NOT NULL, default `0`
- `avg_buy_price` NUMERIC(18,2), nullable
- `created_at` TIMESTAMP, NOT NULL
- `updated_at` TIMESTAMP, NOT NULL

Constraints and indexes:
- primary key: `pk_portfolio_holdings` on (`user_id`, `stock_id`)
- index: `idx_portfolio_holdings_user_id` on (`user_id`)

### `portfolio_transactions`

Purpose:
- append-only ledger of buy/sell activity used for history and PnL calculations

Columns:
- `transaction_id` VARCHAR, PK (sequence-backed in current runtime DB)
- `user_id` BIGINT, NOT NULL
- `stock_id` BIGINT, NOT NULL
- `transaction_type` VARCHAR(20), NOT NULL
- `price` NUMERIC(18,2), NOT NULL
- `quantity` NUMERIC(18,2), NOT NULL
- `executed_at` TIMESTAMP, NOT NULL

Constraints and indexes:
- primary key: `portfolio_transactions_pkey` on (`transaction_id`)
- indexes: `idx_portfolio_transaction_user_id`, `idx_portfolio_transaction_stock_id`

## 7. Stock Service schema

### `stocks`

Purpose:
- master stock catalog used by home, search, watchlist, cart, and portfolio screens

Columns:
- `stock_id` BIGINT, PK
- `ticker` VARCHAR(20), NOT NULL, UNIQUE
- `name` VARCHAR(255), NOT NULL
- `exchange_id` INTEGER, nullable
- `market` VARCHAR(50), nullable
- `locale` VARCHAR(50), nullable
- `type` VARCHAR(50), nullable
- `active` BOOLEAN, nullable/default depending on seed or entity mapping
- `sic_code` VARCHAR, nullable
- `sic_description` VARCHAR, nullable
- `cik` VARCHAR, nullable
- `homepage_url` VARCHAR, nullable
- `list_date` DATE, nullable
- `market_cap` NUMERIC, nullable
- `updated_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `stocks_pkey` on (`stock_id`)
- unique constraint: `stocks_ticker_key` on (`ticker`)
- indexes: `idx_stock_active`, `idx_stock_exchange_id`

### Additional stock-service cache/support tables

### `featured_stocks_cache`

Purpose:
- persists featured-stock list snapshots and order used by home/featured APIs

Columns:
- `featured_cache_id` BIGINT, PK
- `stock_id` BIGINT, NOT NULL, UNIQUE, FK -> `stocks.stock_id`
- `sort_order` INTEGER, NOT NULL
- `cached_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `featured_stocks_cache_pkey` on (`featured_cache_id`)
- unique constraint: `uk_featured_stock_id` on (`stock_id`)
- foreign key: `fkspqix31x34by7u6ayfjk82lxs` on (`stock_id`)
- indexes: `idx_featured_cache_sort_order`

### `all_stocks_last_value_cache`

Purpose:
- persists latest market snapshot per stock for all-stocks listing endpoints

Columns:
- `all_stocks_cache_id` BIGINT, PK
- `stock_id` BIGINT, NOT NULL, UNIQUE, FK -> `stocks.stock_id`
- `cached_open` NUMERIC, NOT NULL
- `cached_high` NUMERIC, NOT NULL
- `cached_low` NUMERIC, NOT NULL
- `cached_close` NUMERIC, NOT NULL
- `cached_volume` BIGINT, NOT NULL
- `cached_vwap` NUMERIC, NOT NULL
- `cached_change_percent` NUMERIC, nullable
- `aggregate_updated_at` TIMESTAMPTZ, nullable
- `cached_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `all_stocks_last_value_cache_pkey` on (`all_stocks_cache_id`)
- unique constraint: `uk_all_stocks_last_value_stock_id` on (`stock_id`)
- foreign key: `fkm8m8tdtetfvf3bdydynwjph5f` on (`stock_id`)
- indexes: `idx_all_stocks_cache_cached_at`, `idx_all_stocks_cache_aggregate_ts`

### `exchanges`

Purpose:
- exchange metadata used for stock catalog enrichment and filtering

Columns:
- `exchange_id` INTEGER, PK
- `name` VARCHAR, NOT NULL
- `mic` VARCHAR, UNIQUE
- `acronym` VARCHAR, nullable
- `asset_class` VARCHAR, nullable
- `locale` VARCHAR, nullable
- `operating_mic` VARCHAR, nullable
- `participant_id` VARCHAR, nullable
- `status` VARCHAR, nullable
- `type` VARCHAR, nullable
- `url` VARCHAR, nullable

Constraints and indexes:
- primary key: `exchanges_pkey` on (`exchange_id`)
- unique constraint: `uksd27rnr32ktsrl78c0erbhdqy` on (`mic`)

## 8. Analytics Service schema

### `stocks` (replica)

Purpose:
- replica of stock identity metadata used by analytics/ML jobs

Columns:
- `stock_id` BIGINT, PK
- `ticker` VARCHAR(20), NOT NULL, UNIQUE
- `name` VARCHAR(255), nullable
- `market` VARCHAR(50), nullable
- `market_cap` NUMERIC(20,2), nullable
- `updated_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `stocks_pkey` on (`stock_id`)
- unique constraint: `stocks_ticker_key` on (`ticker`)

### `stock_daily_ohlc`

Purpose:
- historical daily price and volume data per stock
- source for overlapping 20d/60d/90d volatility values used by insights and weekly feature snapshots

Columns:
- `id` BIGINT, PK
- `stock_id` BIGINT, NOT NULL, FK -> `stocks.stock_id`
- `trading_date` DATE, NOT NULL
- `open_price` NUMERIC(12,2), NOT NULL
- `high_price` NUMERIC(12,2), NOT NULL
- `low_price` NUMERIC(12,2), NOT NULL
- `close_price` NUMERIC(12,2), NOT NULL
- `volume` BIGINT, NOT NULL, default 0
- `sma_20` NUMERIC(12,2), nullable
- `sma_50` NUMERIC(12,2), nullable
- `sma_200` NUMERIC(12,2), nullable
- `volatility_20d` NUMERIC(12,2), nullable
- `volatility_60d` NUMERIC(12,2), nullable
- `volatility_90d` NUMERIC(12,2), nullable
- `return_1d` NUMERIC(12,2), nullable
- `updated_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `stock_daily_ohlc_pkey` on (`id`)
- foreign key: `stock_daily_ohlc_stock_id_fkey` on (`stock_id`)
- unique constraint: `stock_daily_ohlc_stock_id_trading_date_key` on (`stock_id`, `trading_date`)

### `stock_metrics`

Purpose:
- latest per-stock computed metrics used by insights and prediction input
- overlapping 20d/60d/90d volatility values are sourced from `stock_daily_ohlc` and not duplicated here
- includes cached prediction outputs (`prediction_*`) for frontend-visible stocks

Columns:
- `stock_id` BIGINT, PK, FK -> `stocks.stock_id`
- `week_return` NUMERIC(12,2), nullable
- `month_return` NUMERIC(12,2), nullable
- `three_month_return` NUMERIC(12,2), nullable
- `six_month_return` NUMERIC(12,2), nullable
- `year_return` NUMERIC(12,2), nullable
- `three_year_return` NUMERIC(12,2), nullable
- `volatility_5d` NUMERIC(12,2), nullable
- `volatility_120d` NUMERIC(12,2), nullable
- `high_52w` NUMERIC(12,2), nullable
- `low_52w` NUMERIC(12,2), nullable
- `distance_from_high_percent` NUMERIC(12,2), nullable
- `distance_from_low_percent` NUMERIC(12,2), nullable
- `avg_volume_30d` NUMERIC(18,2), nullable
- `latest_trading_day_volume` BIGINT, nullable
- `latest_trading_date` DATE, nullable
- `relative_volume` NUMERIC(12,2), nullable
- `rsi_14` NUMERIC(8,4), nullable
- `macd` NUMERIC(12,4), nullable
- `macd_signal` NUMERIC(12,4), nullable
- `positive_days_1y` INTEGER, nullable
- `negative_days_1y` INTEGER, nullable
- `flat_days_1y` INTEGER, nullable
- `monthly_returns_heatmap` TEXT, nullable
- `max_drawdown` NUMERIC(12,2), nullable
- `drawdown_peak_date` DATE, nullable
- `drawdown_trough_date` DATE, nullable
- `sharpe_ratio` NUMERIC(12,2), nullable
- `sortino_ratio` NUMERIC(12,2), nullable
- `golden_cross` BOOLEAN, nullable
- `death_cross` BOOLEAN, nullable
- `latest_news` TEXT, nullable
- `return_5d` NUMERIC(12,2), nullable
- `return_10d` NUMERIC(12,2), nullable
- `return_20d` NUMERIC(12,2), nullable
- `volatility_10d` NUMERIC(12,2), nullable
- `sma20_distance` NUMERIC(12,2), nullable
- `sma50_distance` NUMERIC(12,2), nullable
- `volume_change` NUMERIC(12,2), nullable
- `label` SMALLINT, nullable
- prediction fields: `prediction_action`, `prediction_confidence`, `prediction_probability_buy`, `prediction_probability_sell`, `prediction_confidence_edge`, `prediction_probability_gap`, `prediction_conviction_label`, `prediction_reasoning`, `prediction_model_version`, `prediction_horizon_days`, `prediction_decision_threshold`, `prediction_generated_at`
- `updated_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `stock_metrics_pkey` on (`stock_id`)
- foreign key: `stock_metrics_stock_id_fkey` on (`stock_id`)

### `ml_weekly_features`

Purpose:
- weekly snapshots for top stocks used as model training history
- copies `volatility_20d` from the latest `stock_daily_ohlc` row during the weekly upsert

Columns:
- `id` BIGINT, PK
- `stock_id` BIGINT, NOT NULL, FK -> `stocks.stock_id`
- `date` DATE, NOT NULL
- `return_5d` NUMERIC(12,2), NOT NULL
- `return_10d` NUMERIC(12,2), NOT NULL
- `return_20d` NUMERIC(12,2), NOT NULL
- `volatility_5d` NUMERIC(12,2), NOT NULL
- `volatility_10d` NUMERIC(12,2), NOT NULL
- `volatility_20d` NUMERIC(12,2), NOT NULL
- `sma20_distance` NUMERIC(12,2), NOT NULL
- `sma50_distance` NUMERIC(12,2), NOT NULL
- `rsi` NUMERIC(12,2), NOT NULL
- `macd` NUMERIC(12,2), NOT NULL
- `volume_change` NUMERIC(12,2), NOT NULL
- `label` SMALLINT, NOT NULL
- `created_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `ml_weekly_features_pkey` on (`id`)
- foreign key: `ml_weekly_features_stock_id_fkey` on (`stock_id`)
- unique index: `idx_ml_weekly_features_stock_date` on (`stock_id`, `date`)

### `ml_model_registry`

Purpose:
- selected model metadata and quality metrics by `model_version`

Columns:
- `model_version` VARCHAR(64), PK
- `model_name` VARCHAR(100), NOT NULL
- `horizon_days` INTEGER, NOT NULL
- `decision_threshold` DOUBLE PRECISION, NOT NULL, default `0.55`
- `trained_rows` INTEGER, NOT NULL
- `cv_f1` DOUBLE PRECISION, NOT NULL
- `test_f1` DOUBLE PRECISION, NOT NULL
- `test_balanced_accuracy` DOUBLE PRECISION, NOT NULL
- `test_precision` DOUBLE PRECISION, nullable
- `test_recall` DOUBLE PRECISION, nullable
- `created_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `ml_model_registry_pkey` on (`model_version`)

### `ml_model_candidates`

Purpose:
- ranked candidate models and metrics for each training run/version
- current candidate families: logistic regression, random forest, gradient boosting, xgboost, knn, svm

Columns:
- `candidate_id` BIGINT, PK
- `model_version` VARCHAR(64), NOT NULL
- `model_name` VARCHAR(100), NOT NULL
- `model_rank` INTEGER, NOT NULL
- `is_selected` BOOLEAN, NOT NULL, default `false`
- `cv_f1` DOUBLE PRECISION, NOT NULL
- `test_f1` DOUBLE PRECISION, NOT NULL
- `test_balanced_accuracy` DOUBLE PRECISION, NOT NULL
- `test_precision` DOUBLE PRECISION, nullable
- `test_recall` DOUBLE PRECISION, nullable
- `created_at` TIMESTAMPTZ, NOT NULL

Constraints and indexes:
- primary key: `ml_model_candidates_pkey` on (`candidate_id`)
- unique constraint: `ml_model_candidates_model_version_model_name_key` on (`model_version`, `model_name`)

## 9. Cross-service identity model

There is no shared relational foreign-key graph across services.

Instead:
- `user_id` is the logical identity link between auth, customer, order, and payment domains
- `stock_id` is the logical stock link between stock, cart, order, watchlist, and portfolio domains
- integrity across services is enforced in application logic, not cross-database foreign keys

## 10. Indexing strategy summary

Already visible in entity mappings:

- user-scoped indexes on carts, orders, transactions: `idx_cart_items_user_id`, `idx_order_user_id_created_at`, `idx_wallet_transaction_wallet_id_created_at`, `idx_portfolio_transaction_user_id`
- order/payment status indexes for operational lookups: `idx_order_status`, `idx_payment_status`, `idx_payment_order_id`
- stock active-state and exchange indexes for catalog queries: `idx_stock_active`, `idx_stock_exchange_id`
- featured cache sort index for ranked home page listing: `idx_featured_cache_sort_order`
- cache timestamp indexes for market data recency: `idx_all_stocks_cache_cached_at`, `idx_all_stocks_cache_aggregate_ts`
- OHLC uniqueness constraint doubles as lookup index: `stock_daily_ohlc_stock_id_trading_date_key`
- wallet transaction history composite index: `idx_wallet_transaction_wallet_id_created_at`, `idx_wallet_transaction_created_at`


## 11. Data design strengths

- service-owned persistence boundaries are clear
- hot user-path queries have explicit indexes in the JPA models
- historical stock data is separated from stock identity metadata
- portfolios use both current-state holdings and event-style transactions
- wallets use both current balance and transaction ledger

## 12. Next database improvements for a hosted production deployment

Recommended future upgrades:

- move from `ddl-auto=update` to controlled migrations
- add backup/restore automation per database
- add partitioning or archiving plans for high-growth history tables
- define retention policies for analytics/event data
- add database-level observability for slow queries and connection saturation

