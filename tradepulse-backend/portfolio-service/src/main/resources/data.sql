CREATE TABLE IF NOT EXISTS portfolio_holdings
(
    user_id         BIGINT         NOT NULL,
    stock_id        BIGINT         NOT NULL,
    total_quantity  NUMERIC(18, 2) NOT NULL DEFAULT 0,
    avg_buy_price   NUMERIC(18, 2),
    created_at      TIMESTAMP      NOT NULL,
    updated_at      TIMESTAMP      NOT NULL,
    CONSTRAINT pk_portfolio_holdings PRIMARY KEY (user_id, stock_id)
);

CREATE INDEX IF NOT EXISTS idx_portfolio_holdings_user_id ON portfolio_holdings (user_id);

CREATE TABLE IF NOT EXISTS portfolio_transactions
(
    transaction_id   BIGSERIAL      PRIMARY KEY,
    user_id          BIGINT         NOT NULL,
    stock_id         BIGINT         NOT NULL,
    transaction_type VARCHAR(20)    NOT NULL,
    price            NUMERIC(18, 2) NOT NULL,
    quantity         NUMERIC(18, 2) NOT NULL,
    executed_at      TIMESTAMP      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_portfolio_transaction_user_id  ON portfolio_transactions (user_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_transaction_stock_id ON portfolio_transactions (stock_id);

