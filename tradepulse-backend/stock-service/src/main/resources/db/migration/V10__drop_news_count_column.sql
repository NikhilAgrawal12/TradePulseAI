-- Remove the legacy news_count column from stock_daily_ohlc.
-- Sentiment score remains; only the count column is dropped.
ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS news_count;

