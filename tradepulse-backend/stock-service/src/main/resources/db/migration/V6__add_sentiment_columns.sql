-- Migration: Add sentiment and news columns to stock_daily_ohlc table
-- This migration adds support for daily news sentiment analysis

ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS sentiment_score NUMERIC(5, 4);

-- Create index for sentiment queries
CREATE INDEX IF NOT EXISTS idx_stock_daily_ohlc_sentiment
ON stock_daily_ohlc(stock_id, trading_date, sentiment_score);

-- Add comments for documentation
COMMENT ON COLUMN stock_daily_ohlc.sentiment_score IS 'Daily sentiment score: -1.0 (very negative) to 1.0 (very positive)';


