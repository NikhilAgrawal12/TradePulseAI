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
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS return_5d       NUMERIC(12, 4);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS return_90d      NUMERIC(12, 4);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS volatility_5d NUMERIC(12, 2);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS volatility_20d NUMERIC(12, 2);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS volatility_60d NUMERIC(12, 2);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS volatility_120d NUMERIC(12, 2);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS volatility_90d NUMERIC(12, 2);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS return_1d NUMERIC(12, 2);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS forward_return_5d NUMERIC(12, 4);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS target_week_direction VARCHAR(16);

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
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS positive_days_1y INT;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS negative_days_1y INT;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS flat_days_1y INT;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS monthly_returns_heatmap TEXT;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS max_drawdown NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS drawdown_peak_date DATE;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS drawdown_trough_date DATE;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS sharpe_ratio NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS sortino_ratio NUMERIC(12, 2);
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS golden_cross BOOLEAN;
ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS death_cross BOOLEAN;

-- Drop unused stock_metrics columns now computed directly from OHLC/realtime paths
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS current_price;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS sma_20;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS sma_50;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS sma_200;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS volatility_1y;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS volatility_30d;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS volatility_90d;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS rsi_14;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS macd;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS macd_signal;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS momentum_30d;

-- Drop unused stock_daily_ohlc columns no longer fetched from API
ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS is_otc;
ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS adjusted;
ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS momentum_20d;
ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS volatility_30d;

-- Ensure ML feature columns exist (guard for services that skip Flyway)
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS return_5d       NUMERIC(12, 4);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS return_20d      NUMERIC(12, 4);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS return_60d      NUMERIC(12, 4);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS return_120d     NUMERIC(12, 4);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS rsi_14          NUMERIC(8,  4);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS macd            NUMERIC(12, 4);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS macd_signal     NUMERIC(12, 4);

-- Ensure sentiment columns exist
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS sentiment_score NUMERIC(5, 4);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS daily_news      TEXT;
ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS news_count;

-- -----------------------------------------------------------------------
-- Backfill newly added technical indicator columns for ALL existing rows.
-- These UPDATEs are guarded by WHERE ... IS NULL so they are cheap on
-- subsequent startups once the values have already been written.
-- The Java StockMetricsRefreshService overwrites these for recent rows
-- with its more accurate EMA-based computation on first sync.
-- -----------------------------------------------------------------------

-- return_5d: 5-day % change in close price
UPDATE stock_daily_ohlc d
SET return_5d = subq.return_5d
FROM (
    SELECT
        stock_id,
        trading_date,
        ROUND(
            (close_price - LAG(close_price, 5) OVER (PARTITION BY stock_id ORDER BY trading_date))
            / NULLIF(LAG(close_price, 5) OVER (PARTITION BY stock_id ORDER BY trading_date), 0) * 100,
            4
        ) AS return_5d
    FROM stock_daily_ohlc
) subq
WHERE d.stock_id = subq.stock_id
  AND d.trading_date = subq.trading_date
  AND d.return_5d IS NULL
  AND subq.return_5d IS NOT NULL;

-- return_20d / return_60d / return_120d: multi-horizon % changes in close price
UPDATE stock_daily_ohlc d
SET
    return_20d = subq.return_20d,
    return_60d = subq.return_60d,
    return_120d = subq.return_120d
FROM (
    SELECT
        stock_id,
        trading_date,
        ROUND(
            (close_price - LAG(close_price, 20) OVER w)
            / NULLIF(LAG(close_price, 20) OVER w, 0) * 100,
            4
        ) AS return_20d,
        ROUND(
            (close_price - LAG(close_price, 60) OVER w)
            / NULLIF(LAG(close_price, 60) OVER w, 0) * 100,
            4
        ) AS return_60d,
        ROUND(
            (close_price - LAG(close_price, 120) OVER w)
            / NULLIF(LAG(close_price, 120) OVER w, 0) * 100,
            4
        ) AS return_120d
    FROM stock_daily_ohlc
    WINDOW w AS (PARTITION BY stock_id ORDER BY trading_date)
) subq
WHERE d.stock_id = subq.stock_id
  AND d.trading_date = subq.trading_date
  AND (
      d.return_20d IS NULL
      OR d.return_60d IS NULL
      OR d.return_120d IS NULL
  );

-- volatility windows aligned to return horizons (exclude 1d)
UPDATE stock_daily_ohlc d
SET
    volatility_5d = subq.volatility_5d,
    volatility_20d = subq.volatility_20d,
    volatility_60d = subq.volatility_60d,
    volatility_120d = subq.volatility_120d
FROM (
    WITH daily_returns AS (
        SELECT
            stock_id,
            trading_date,
            (close_price - LAG(close_price, 1) OVER w)
            / NULLIF(LAG(close_price, 1) OVER w, 0) AS ret_1d
        FROM stock_daily_ohlc
        WINDOW w AS (PARTITION BY stock_id ORDER BY trading_date)
    )
    SELECT
        stock_id,
        trading_date,
        CASE
            WHEN COUNT(ret_1d) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 4 PRECEDING AND CURRENT ROW) = 5
            THEN ROUND(
                (
                    STDDEV_SAMP(ret_1d) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 4 PRECEDING AND CURRENT ROW)
                    * SQRT(252) * 100
                )::NUMERIC,
                2
            )
            ELSE NULL
        END AS volatility_5d,
        CASE
            WHEN COUNT(ret_1d) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 19 PRECEDING AND CURRENT ROW) = 20
            THEN ROUND(
                (
                    STDDEV_SAMP(ret_1d) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 19 PRECEDING AND CURRENT ROW)
                    * SQRT(252) * 100
                )::NUMERIC,
                2
            )
            ELSE NULL
        END AS volatility_20d,
        CASE
            WHEN COUNT(ret_1d) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 59 PRECEDING AND CURRENT ROW) = 60
            THEN ROUND(
                (
                    STDDEV_SAMP(ret_1d) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 59 PRECEDING AND CURRENT ROW)
                    * SQRT(252) * 100
                )::NUMERIC,
                2
            )
            ELSE NULL
        END AS volatility_60d,
        CASE
            WHEN COUNT(ret_1d) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 119 PRECEDING AND CURRENT ROW) = 120
            THEN ROUND(
                (
                    STDDEV_SAMP(ret_1d) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 119 PRECEDING AND CURRENT ROW)
                    * SQRT(252) * 100
                )::NUMERIC,
                2
            )
            ELSE NULL
        END AS volatility_120d
    FROM daily_returns
) subq
WHERE d.stock_id = subq.stock_id
  AND d.trading_date = subq.trading_date
  AND (
      d.volatility_5d IS NULL
      OR d.volatility_20d IS NULL
      OR d.volatility_60d IS NULL
      OR d.volatility_120d IS NULL
  );


-- rsi_14: approximated via 14-period average gain/loss window
UPDATE stock_daily_ohlc d
SET rsi_14 = subq.rsi_14
FROM (
    WITH daily_changes AS (
        SELECT stock_id, trading_date,
               close_price - LAG(close_price, 1) OVER (PARTITION BY stock_id ORDER BY trading_date) AS delta
        FROM stock_daily_ohlc
    ),
    gains_losses AS (
        SELECT stock_id, trading_date,
               GREATEST(delta, 0) AS gain,
               GREATEST(-delta, 0) AS loss
        FROM daily_changes WHERE delta IS NOT NULL
    ),
    rsi_raw AS (
        SELECT stock_id, trading_date,
               AVG(gain) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 13 PRECEDING AND CURRENT ROW) AS avg_gain,
               AVG(loss) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 13 PRECEDING AND CURRENT ROW) AS avg_loss
        FROM gains_losses
    )
    SELECT stock_id, trading_date,
           CASE WHEN avg_loss = 0 THEN 100.0000
                ELSE ROUND(100 - (100.0 / (1 + avg_gain / NULLIF(avg_loss, 0))), 4)
           END AS rsi_14
    FROM rsi_raw
) subq
WHERE d.stock_id = subq.stock_id
  AND d.trading_date = subq.trading_date
  AND d.rsi_14 IS NULL
  AND subq.rsi_14 IS NOT NULL;

-- macd and macd_signal: SMA-based approximation (Java service will overwrite with true EMA values)
UPDATE stock_daily_ohlc d
SET macd        = subq.macd_val,
    macd_signal = subq.signal_val
FROM (
    WITH ema_approx AS (
        SELECT stock_id, trading_date,
               ROUND(
                   AVG(close_price) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 11 PRECEDING AND CURRENT ROW)
                   - AVG(close_price) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 25 PRECEDING AND CURRENT ROW),
                   4
               ) AS macd_val
        FROM stock_daily_ohlc
    )
    SELECT stock_id, trading_date, macd_val,
           ROUND(AVG(macd_val) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 8 PRECEDING AND CURRENT ROW), 4) AS signal_val
    FROM ema_approx
    WHERE macd_val IS NOT NULL
) subq
WHERE d.stock_id = subq.stock_id
  AND d.trading_date = subq.trading_date
  AND d.macd IS NULL
  AND subq.macd_val IS NOT NULL;

-- Default sentiment to neutral (0) where no news data has been collected yet
UPDATE stock_daily_ohlc
SET sentiment_score = 0.0000
WHERE sentiment_score IS NULL;

-- forward_return_5d and target_week_direction: label next-week direction for ML
UPDATE stock_daily_ohlc d
SET
    forward_return_5d = subq.forward_return_5d,
    target_week_direction = CASE
        WHEN subq.forward_return_5d > 0 THEN 'POSITIVE'
        WHEN subq.forward_return_5d < 0 THEN 'NEGATIVE'
        WHEN subq.forward_return_5d = 0 THEN 'FLAT'
        ELSE NULL
    END
FROM (
    SELECT
        stock_id,
        trading_date,
        ROUND(
            (LEAD(close_price, 5) OVER (PARTITION BY stock_id ORDER BY trading_date) - close_price)
            / NULLIF(close_price, 0) * 100,
            4
        ) AS forward_return_5d
    FROM stock_daily_ohlc
) subq
WHERE d.stock_id = subq.stock_id
  AND d.trading_date = subq.trading_date
  AND (
      d.forward_return_5d IS NULL
      OR d.target_week_direction IS NULL
  );


