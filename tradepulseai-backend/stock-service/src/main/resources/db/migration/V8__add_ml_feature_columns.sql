-- Add pre-computed ML features to stock_daily_ohlc so the model
-- reads stored values instead of recomputing them at training time.

ALTER TABLE stock_daily_ohlc
    ADD COLUMN IF NOT EXISTS return_5d       NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS momentum_20d    NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS rsi_14          NUMERIC(8,  4),
    ADD COLUMN IF NOT EXISTS macd            NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS macd_signal     NUMERIC(12, 4);

COMMENT ON COLUMN stock_daily_ohlc.return_5d    IS '5-day price return (%)';
COMMENT ON COLUMN stock_daily_ohlc.momentum_20d IS '20-day price momentum (%)';
COMMENT ON COLUMN stock_daily_ohlc.rsi_14       IS '14-period Relative Strength Index';
COMMENT ON COLUMN stock_daily_ohlc.macd         IS 'MACD line (EMA12 - EMA26)';
COMMENT ON COLUMN stock_daily_ohlc.macd_signal  IS 'MACD signal line (EMA9 of MACD)';

