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

-- Ensure stock_daily_ohlc has precomputed indicator columns used by insights history charts/table
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS sma_20 NUMERIC(12, 2);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS sma_50 NUMERIC(12, 2);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS sma_200 NUMERIC(12, 2);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS volatility_30d NUMERIC(12, 2);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS volatility_90d NUMERIC(12, 2);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS daily_return_percent NUMERIC(12, 2);

-- Ensure stock_metrics has all returns windows used by Insights Returns tab
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS three_month_return NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS six_month_return NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS three_year_return NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS distance_from_high_percent NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS distance_from_low_percent NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS avg_volume_30d NUMERIC(20, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS latest_trading_day_volume BIGINT;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS latest_trading_date DATE;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS relative_volume NUMERIC(12, 4);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS volatility_30d NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS volatility_90d NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS positive_days_1y INT;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS negative_days_1y INT;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS flat_days_1y INT;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS monthly_returns_heatmap TEXT;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS max_drawdown NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS drawdown_peak_date DATE;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS drawdown_trough_date DATE;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS sharpe_ratio NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS sortino_ratio NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS rsi_14 NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS macd NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS macd_signal NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS momentum_30d NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS golden_cross BOOLEAN;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS death_cross BOOLEAN;

-- Drop unused stock_metrics columns now computed directly from OHLC/realtime paths
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS current_price;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS sma_20;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS sma_50;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS sma_200;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS volatility_1y;

