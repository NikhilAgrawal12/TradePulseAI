-- Add compact daily news text for analytics UI and remove unused OHLC columns.

ALTER TABLE stock_daily_ohlc
    ADD COLUMN IF NOT EXISTS daily_news TEXT;

-- These columns are not used by the current frontend or ML pipeline.
ALTER TABLE stock_daily_ohlc
    DROP COLUMN IF EXISTS is_otc,
    DROP COLUMN IF EXISTS adjusted;

