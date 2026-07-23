-- Momentum is removed from insights payload and storage to avoid duplicate return-style metrics.
ALTER TABLE stock_metrics DROP COLUMN IF EXISTS momentum_30d;
ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS momentum_20d;

