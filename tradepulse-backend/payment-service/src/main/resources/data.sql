-- Keep this script non-empty when spring.sql.init.mode=always.
SELECT 1;

-- Convert payments.order_id from bigint to varchar so UUID order IDs from order-service are supported.
ALTER TABLE IF EXISTS payments
    ALTER COLUMN order_id TYPE VARCHAR(64)
    USING order_id::text;

-- Wallet tables (created by Hibernate DDL, these are safety fallbacks)
CREATE TABLE IF NOT EXISTS wallets (
    wallet_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    transaction_id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
