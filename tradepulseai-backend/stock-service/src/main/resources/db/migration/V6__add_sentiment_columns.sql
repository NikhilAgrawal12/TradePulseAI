-- Migration: Add sentiment and news columns to stock_daily_ohlc table
-- This migration adds support for daily news sentiment analysis

ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS daily_sentiment VARCHAR(20);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS sentiment_score NUMERIC(5, 4);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS news_count INT DEFAULT 0;
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS news_summary TEXT;
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS news_sources VARCHAR(500);
ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS sentiment_reasoning TEXT;

-- Create index for sentiment queries
CREATE INDEX IF NOT EXISTS idx_stock_daily_ohlc_sentiment
ON stock_daily_ohlc(stock_id, trading_date, daily_sentiment);

-- Add comments for documentation
COMMENT ON COLUMN stock_daily_ohlc.daily_sentiment IS 'Daily sentiment label: positive, negative, or neutral';
COMMENT ON COLUMN stock_daily_ohlc.sentiment_score IS 'Daily sentiment score: -1.0 (very negative) to 1.0 (very positive)';
COMMENT ON COLUMN stock_daily_ohlc.news_count IS 'Number of news articles published for this stock on this date';
COMMENT ON COLUMN stock_daily_ohlc.news_summary IS 'Concatenated titles of news articles for the day';
COMMENT ON COLUMN stock_daily_ohlc.news_sources IS 'Comma-separated list of news publishers';
COMMENT ON COLUMN stock_daily_ohlc.sentiment_reasoning IS 'Explanation of sentiment: positive count, negative count, neutral count, and composite score';

