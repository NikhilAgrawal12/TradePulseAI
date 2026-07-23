-- Drop technical indicators that now come from latest stock_daily_ohlc rows.
-- Keep these columns only on stock_daily_ohlc to avoid duplicated storage.
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS volatility_30d;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS volatility_90d;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS rsi_14;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS macd;
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS macd_signal;

