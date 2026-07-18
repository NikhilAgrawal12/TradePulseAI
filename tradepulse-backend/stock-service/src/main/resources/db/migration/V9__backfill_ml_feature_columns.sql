-- Backfill newly added technical indicator columns (return_5d, momentum_20d, rsi_14, macd, macd_signal)
-- for all existing rows using PostgreSQL window functions.
-- The Java service will also overwrite these on next metrics refresh, but this ensures
-- ML training has values immediately after migration.

-- return_5d: 5-day % change in close price
UPDATE stock_daily_ohlc d
SET return_5d = subq.return_5d
FROM (
    SELECT
        stock_id,
        trading_date,
        ROUND(
            (close_price - LAG(close_price, 5) OVER w) / NULLIF(LAG(close_price, 5) OVER w, 0) * 100,
            4
        ) AS return_5d
    FROM stock_daily_ohlc
    WINDOW w AS (PARTITION BY stock_id ORDER BY trading_date)
) subq
WHERE d.stock_id = subq.stock_id
  AND d.trading_date = subq.trading_date
  AND subq.return_5d IS NOT NULL;

-- momentum_20d: 20-day % change in close price
UPDATE stock_daily_ohlc d
SET momentum_20d = subq.momentum_20d
FROM (
    SELECT
        stock_id,
        trading_date,
        ROUND(
            (close_price - LAG(close_price, 20) OVER w) / NULLIF(LAG(close_price, 20) OVER w, 0) * 100,
            4
        ) AS momentum_20d
    FROM stock_daily_ohlc
    WINDOW w AS (PARTITION BY stock_id ORDER BY trading_date)
) subq
WHERE d.stock_id = subq.stock_id
  AND d.trading_date = subq.trading_date
  AND subq.momentum_20d IS NOT NULL;

-- rsi_14: simplified approximation using a 14-period average gain/loss via window functions
-- Note: The Java service computes a more accurate RSI; this seeds initial values.
UPDATE stock_daily_ohlc d
SET rsi_14 = subq.rsi_14
FROM (
    WITH daily_changes AS (
        SELECT
            stock_id,
            trading_date,
            close_price - LAG(close_price, 1) OVER (PARTITION BY stock_id ORDER BY trading_date) AS delta
        FROM stock_daily_ohlc
    ),
    gains_losses AS (
        SELECT
            stock_id,
            trading_date,
            GREATEST(delta, 0) AS gain,
            GREATEST(-delta, 0) AS loss
        FROM daily_changes
        WHERE delta IS NOT NULL
    ),
    averages AS (
        SELECT
            stock_id,
            trading_date,
            AVG(gain) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 13 PRECEDING AND CURRENT ROW) AS avg_gain,
            AVG(loss) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 13 PRECEDING AND CURRENT ROW) AS avg_loss
        FROM gains_losses
    )
    SELECT
        stock_id,
        trading_date,
        CASE
            WHEN avg_loss = 0 THEN 100
            ELSE ROUND(100 - (100 / (1 + avg_gain / NULLIF(avg_loss, 0))), 4)
        END AS rsi_14
    FROM averages
) subq
WHERE d.stock_id = subq.stock_id
  AND d.trading_date = subq.trading_date
  AND subq.rsi_14 IS NOT NULL;

-- macd and macd_signal: EMA-based indicators, approximated with SQL exponential smoothing
-- Full accuracy computed by Java service on next metrics refresh.
-- We use a simplified 12-period vs 26-period EMA approximation via AVG as initial seed.
UPDATE stock_daily_ohlc d
SET
    macd = subq.macd_val,
    macd_signal = subq.signal_val
FROM (
    WITH ema_approx AS (
        SELECT
            stock_id,
            trading_date,
            ROUND(
                AVG(close_price) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 11 PRECEDING AND CURRENT ROW)
                - AVG(close_price) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 25 PRECEDING AND CURRENT ROW),
                4
            ) AS macd_val
        FROM stock_daily_ohlc
    )
    SELECT
        stock_id,
        trading_date,
        macd_val,
        ROUND(
            AVG(macd_val) OVER (PARTITION BY stock_id ORDER BY trading_date ROWS BETWEEN 8 PRECEDING AND CURRENT ROW),
            4
        ) AS signal_val
    FROM ema_approx
    WHERE macd_val IS NOT NULL
) subq
WHERE d.stock_id = subq.stock_id
  AND d.trading_date = subq.trading_date
  AND subq.macd_val IS NOT NULL;

-- Backfill sentiment defaults for rows with no news data yet
UPDATE stock_daily_ohlc
SET sentiment_score = 0.0000
WHERE sentiment_score IS NULL;

