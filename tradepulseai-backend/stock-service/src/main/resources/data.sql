-- Keep this script non-empty because spring.sql.init.mode=always runs it at startup.
SELECT 1;

-- Ensure market_cap column exists (used for ranking)
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS market_cap NUMERIC(22, 2);

-- Create featured_stocks_cache table to hold top 50 stocks ranking
CREATE TABLE IF NOT EXISTS featured_stocks_cache (
    cache_id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT NOT NULL UNIQUE,
    sort_order INTEGER NOT NULL,
    cached_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_featured_cache_stock_id FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_featured_cache_sort_order ON featured_stocks_cache(sort_order ASC);
CREATE INDEX IF NOT EXISTS idx_featured_cache_stock_id ON featured_stocks_cache(stock_id);

-- Create all_stocks_last_value_cache table to hold latest aggregate data for ALL stocks
-- Stores: open, close, high, low, volume, vwap, change_percent (per-second aggregate data)
CREATE TABLE IF NOT EXISTS all_stocks_last_value_cache (
    cache_id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT NOT NULL UNIQUE,
    cached_open NUMERIC(18, 6) NOT NULL,
    cached_close NUMERIC(18, 6) NOT NULL,
    cached_high NUMERIC(18, 6) NOT NULL,
    cached_low NUMERIC(18, 6) NOT NULL,
    cached_volume BIGINT NOT NULL,
    cached_vwap NUMERIC(18, 6) NOT NULL,
    cached_change_percent NUMERIC(12, 6),
    aggregate_updated_at TIMESTAMP,
    cached_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_all_stocks_cache_stock_id FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE
);

-- Create indexes for faster lookups
CREATE INDEX IF NOT EXISTS idx_all_stocks_cache_stock_id ON all_stocks_last_value_cache(stock_id);
CREATE INDEX IF NOT EXISTS idx_all_stocks_cache_cached_at ON all_stocks_last_value_cache(cached_at DESC);
CREATE INDEX IF NOT EXISTS idx_all_stocks_cache_aggregate_ts ON all_stocks_last_value_cache(aggregate_updated_at DESC);

