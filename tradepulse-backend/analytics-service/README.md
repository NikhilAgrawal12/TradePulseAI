# TradePulse Analytics Service

Python FastAPI service that owns analytics storage and serves ML signals.

## What it does
- Stores analytics data in `analytics-service-db` only
- Keeps a `stocks` replica synced from stock-service (`GET /stocks`)
- Fetches grouped daily OHLC from Massive API
- Computes `stock_metrics` + `ml_weekly_features` via PySpark
- Trains using `ml_weekly_features` (`avg_return`, `volatility`, `next_week_label`)
- Fetches latest Massive news and stores it in `stock_metrics.latest_news`
- Trains and serves a buy/sell signal model
- Retrains model on startup/schedule (configurable)

## Tables owned in analytics-service-db
- `stocks` (replica)
- `stock_daily_ohlc`
- `stock_metrics`
- `ml_weekly_features`
- `ml_model_registry`
- `ml_model_candidates`

## API
- `GET /health`
- `POST /v1/train`
- `GET /v1/predictions/{stock_id}`
- `POST /v1/admin/sync-nightly` (manual nightly pipeline trigger)
- `GET /v1/admin/sync-status`

## Environment variables
- `ML_DATABASE_URL`
- `ML_MODEL_PATH`
- `ML_SERVICE_PORT`
- `ML_DEFAULT_DAYS_BACK`
- `ML_DEFAULT_HORIZON_DAYS`
- `ML_DEFAULT_POSITIVE_RETURN_THRESHOLD`
- `ML_DEFAULT_NEUTRAL_RETURN_BAND`
- `ML_MAX_TRAINING_STOCKS`
- `ML_TRAIN_ON_STARTUP`
- `ML_RETRAIN_INTERVAL_HOURS`
- `STOCK_SERVICE_BASE_URL` (default: `http://stock-service:4003`)
- `STOCK_SERVICE_TIMEOUT_SECONDS` (default: `30`)
- `STOCK_REPLICA_SYNC_ENABLED` (default: `false`; keep `false` if analytics-service must not call stock-service)
- `MASSIVE_API_BASE_URL` (default: `https://api.massive.com`)
- `MASSIVE_API_KEY` (required for OHLC ingestion)
- `MASSIVE_NEWS_LIMIT` (default: `5`)
- `OHLC_YEARS_BACK` (default: `3`)
- `OHLC_ADJUSTED` (default: `true`)
- `OHLC_INCLUDE_OTC` (default: `false`)
- `NIGHTLY_SYNC_ENABLED` (default: `true`)
- `NIGHTLY_SYNC_ON_STARTUP` (default: `true`)
- `NIGHTLY_SYNC_INTERVAL_HOURS` (default: `24`)

## Java requirement for PySpark
- Metrics refresh uses PySpark, so a JDK (11+) must be available.
- In Docker, Java is already installed in the service image.
- For local runs, either set `JAVA_HOME` or add `java` to your system `PATH`.

## Local run
```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 4010
```

## Run tests
```bash
pytest -q
```
