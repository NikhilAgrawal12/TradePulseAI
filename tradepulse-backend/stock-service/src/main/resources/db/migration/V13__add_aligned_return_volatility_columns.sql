-- Add aligned return/volatility windows for ML training on return + risk features.
-- Convention: use trading-day windows and skip 1d volatility.

ALTER TABLE stock_daily_ohlc
    ADD COLUMN IF NOT EXISTS return_20d       NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS return_60d       NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS return_120d      NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS volatility_5d    NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS volatility_20d   NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS volatility_60d   NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS volatility_120d  NUMERIC(12, 2);

COMMENT ON COLUMN stock_daily_ohlc.return_20d      IS '20-day price return (%)';
COMMENT ON COLUMN stock_daily_ohlc.return_60d      IS '60-day price return (%)';
COMMENT ON COLUMN stock_daily_ohlc.return_120d     IS '120-day price return (%)';
COMMENT ON COLUMN stock_daily_ohlc.volatility_5d   IS '5-day annualized volatility (%)';
COMMENT ON COLUMN stock_daily_ohlc.volatility_20d  IS '20-day annualized volatility (%)';
COMMENT ON COLUMN stock_daily_ohlc.volatility_60d  IS '60-day annualized volatility (%)';
COMMENT ON COLUMN stock_daily_ohlc.volatility_120d IS '120-day annualized volatility (%)';

-- Backfill return windows for all existing rows.
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
  AND d.trading_date = subq.trading_date;

-- Backfill annualized volatility windows from 1-day returns for all existing rows.
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
  AND d.trading_date = subq.trading_date;



