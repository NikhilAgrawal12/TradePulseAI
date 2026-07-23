-- Add forward 5-day return and weekly direction label for ML targeting.
ALTER TABLE stock_daily_ohlc
    ADD COLUMN IF NOT EXISTS forward_return_5d NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS target_week_direction VARCHAR(16);

COMMENT ON COLUMN stock_daily_ohlc.forward_return_5d IS 'Forward 5-trading-day return (%) from current row close';
COMMENT ON COLUMN stock_daily_ohlc.target_week_direction IS 'Forward 5-day direction label: POSITIVE, NEGATIVE, or FLAT';

-- Backfill forward 5-day return and direction label for all historical rows.
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
  AND d.trading_date = subq.trading_date;

