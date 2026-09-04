CREATE TABLE IF NOT EXISTS quote_locks (
    quote_lock_id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total NUMERIC(18, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quote_locks_user_id ON quote_locks(user_id);
CREATE INDEX IF NOT EXISTS idx_quote_locks_status_expires_at ON quote_locks(status, expires_at);

CREATE TABLE IF NOT EXISTS quote_lock_items (
    quote_lock_id VARCHAR(36) NOT NULL,
    stock_id VARCHAR(255) NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    price NUMERIC(18, 2) NOT NULL,
    quantity NUMERIC(18, 2) NOT NULL,
    PRIMARY KEY (quote_lock_id, stock_id),
    CONSTRAINT fk_quote_lock_items_quote_lock FOREIGN KEY (quote_lock_id)
        REFERENCES quote_locks(quote_lock_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_quote_lock_items_quote_lock_id ON quote_lock_items(quote_lock_id);

