-- Rename daily_return_percent → return_1d for consistent naming convention
ALTER TABLE stock_daily_ohlc RENAME COLUMN daily_return_percent TO return_1d;

-- Add return_90d aligned with existing volatility_90d
ALTER TABLE stock_daily_ohlc
    ADD COLUMN IF NOT EXISTS return_90d NUMERIC(12, 4);

COMMENT ON COLUMN stock_daily_ohlc.return_1d   IS '1-day price return (%)';
COMMENT ON COLUMN stock_daily_ohlc.return_90d  IS '90-day price return (%)';

-- Backfill return_90d for all existing rows
UPDATE stock_daily_ohlc d
SET return_90d = subq.return_90d
FROM (
    SELECT
        stock_id,
        trading_date,
        ROUND(
            (close_price - LAG(close_price, 90) OVER (PARTITION BY stock_id ORDER BY trading_date))
            / NULLIF(LAG(close_price, 90) OVER (PARTITION BY stock_id ORDER BY trading_date), 0) * 100,
            4
        ) AS return_90d
    FROM stock_daily_ohlc
) subq
WHERE d.stock_id = subq.stock_id
  AND d.trading_date = subq.trading_date
  AND subq.return_90d IS NOT NULL;

-- Drop volatility_30d column completely
ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS volatility_30d;

