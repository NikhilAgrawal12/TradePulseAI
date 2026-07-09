# News Sentiment Integration Guide

## Overview

This integration adds daily news sentiment analysis to your ML pipeline. The system fetches news articles from Polygon.io (via Massive API), aggregates sentiment across all articles for a given stock and date, and stores the results in the `stock_daily_ohlc` table for use in model training.

## What Changed

### 1. Database Schema (New Columns in `stock_daily_ohlc`)

- `sentiment_score` - Float (-1.0 to 1.0) representing sentiment from daily news
- `news_count` - Integer count of articles published that day

### 2. Backend Services (Java)

#### `NewsService.java`
- Fetches daily news for a stock from Polygon.io API
- Aggregates sentiment from individual article insights
- Calculates composite sentiment score: `(positive_count - negative_count) / total_articles`
- Stores only the score and count in the database

**Usage:**
```java
newsService.fetchAndUpdateNewsSentiment(stock, tradingDate);
```

#### `NewsIntegrationScheduler.java`
- Scheduled component that runs daily at 4 PM UTC (after US market close)
- Fetches news for all active stocks
- Rate-limited to avoid API throttling
- Supports manual backfill of historical news

**Scheduled Job:** `0 0 16 * * MON-FRI` (UTC)

**Manual Backfill:**
```java
newsIntegrationScheduler.backfillNewsForStock("AAPL", 90);  // 90 days back
```

### 3. Repository Method

Added to `StockMarketDataRepository`:
```java
Optional<StockMarketData> findByStockAndTradingDate(Stock stock, LocalDate tradingDate);
```

### 4. ML Pipeline Features

Two new numeric features added:
- `sentiment_score` - Daily sentiment (-1.0 to 1.0)
- `news_count` - Number of articles that day

These are now included in model training alongside your existing 28 technical features (total 30 features).

**Total Features: 30** (was 28)

### 5. Configuration

Add to `application.properties` or environment variables:

```properties
# Enable news sentiment fetching
massive.news.integration-enabled=true

# Polygon.io API key (required for news fetching)
MASSIVE_API_KEY=your_polygon_io_api_key
```

## Setup Instructions

### 1. Enable News Integration

Set environment variable in `docker-compose.yml`:
```yaml
stock-service:
  environment:
    MASSIVE_NEWS_INTEGRATION_ENABLED: "true"
    MASSIVE_API_KEY: "your_polygon_io_api_key"
```

### 2. Apply Database Migration

The migration `V6__add_sentiment_columns.sql` will automatically run on next service startup (Flyway).

Or manually:
```sql
ALTER TABLE stock_daily_ohlc ADD COLUMN sentiment_score NUMERIC(5, 4);
ALTER TABLE stock_daily_ohlc ADD COLUMN news_count INT DEFAULT 0;
```

### 3. Backfill Historical News (Optional)

After deployment, manually trigger backfill for key stocks:

```bash
# Via HTTP endpoint (if you expose it)
POST /admin/news/backfill
{
  "ticker": "AAPL",
  "daysBack": 90
}
```

Or call the service directly:
```java
@GetMapping("/admin/news/backfill/{ticker}")
public ResponseEntity<String> backfillNews(
    @PathVariable String ticker,
    @RequestParam(defaultValue = "30") int daysBack) {
    newsIntegrationScheduler.backfillNewsForStock(ticker, daysBack);
    return ResponseEntity.ok("Backfill started for " + ticker);
}
```

## How It Works

### Daily Flow

1. **4 PM UTC (16:00)** - `NewsIntegrationScheduler` triggers
2. **For each active stock:**
   - Query Polygon.io `/v2/reference/news` API for that trading date
   - Extract sentiment from each article's `insights.sentiment` field
   - Aggregate sentiments: count positive and negative articles
   - Calculate composite score: `(positive_count - negative_count) / total_articles`
3. **Store results** in `stock_daily_ohlc` for that stock/date
4. **Rate limit** with 100ms sleep between API calls

### Sentiment Calculation

```
sentiment_score = (positive_articles - negative_articles) / total_articles

Example:
- 4 positive articles
- 1 negative article
- 2 neutral articles
- Total: 7 articles
- Score = (4 - 1) / 7 = 0.4286 → Used as feature
```

## ML Model Impact

### Expected Benefits

1. **Additional signal**: News sentiment correlates with price movement
2. **Improved prediction**: Models trained with sentiment typically achieve higher F1 scores
3. **Real-world performance**: Sentiment helps capture regime changes that technical indicators lag

### Feature Importance

Sentiment features (`sentiment_score` and `news_count`) will be automatically ranked by the model during training. Tree-based models (Random Forest, XGBoost, Gradient Boosting) typically find these features moderately important.

### Handling Missing Data

- **No news day**: `sentiment_score = 0.0`, `news_count = 0`
- **Holiday/weekend**: Columns remain NULL (imputed as median during preprocessing)

## Monitoring

### Check Current Sentiment

```sql
SELECT 
  stock_id,
  trading_date,
  sentiment_score,
  news_count
FROM stock_daily_ohlc
WHERE trading_date >= CURRENT_DATE - INTERVAL '7 days'
ORDER BY trading_date DESC;
```

### Verify Latest Update

```sql
SELECT MAX(updated_at) as last_update FROM stock_daily_ohlc;
```

## Troubleshooting

### No News Fetched

1. Check `massive.news.integration-enabled=true`
2. Verify `MASSIVE_API_KEY` is set and valid
3. Check logs: `NewsService` should log fetches at INFO level
4. Verify stock exists in database

### Sentiment Always Neutral

- Polygon.io may not return insights for all articles
- Check API response format matches expected structure
- Manually verify with Polygon.io API directly

### Performance Issues

- Reduce `max_training_rows` if sentiment data slows down model training
- News fetching runs asynchronously and won't block daily OHLC sync

## API Polygon.io Integration

The system uses Polygon.io's `/v2/reference/news` endpoint:

```
GET https://api.massive.com/v2/reference/news
?ticker=AAPL
&published_utc.gte=2026-07-09
&published_utc.lt=2026-07-10
&limit=100
&sort=published_utc
&apiKey=YOUR_KEY
```

**Requirements:**
- Polygon.io Pro plan or higher (Starter plan lacks news)
- API key must have news endpoint access
- Rate limit: ~600 requests/minute (sufficient for ~100 stocks)

## Future Enhancements

1. **Sentiment by category**: Break down sentiment by news category (earnings, SEC filings, etc.)
2. **Multi-language support**: Fetch news in multiple languages
3. **Sentiment trends**: Calculate 5-day and 10-day rolling sentiment averages
4. **Topic extraction**: Identify key topics in news (e.g., "acquisition", "FDA approval")
5. **Real-time updates**: Fetch news intraday for live trading signals

## Support

For questions or issues, refer to:
- Polygon.io documentation: https://polygon.io/docs/stocks/get-ticker-news
- NewsService.java implementation
- Integration test examples






