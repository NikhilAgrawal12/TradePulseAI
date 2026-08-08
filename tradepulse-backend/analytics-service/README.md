# TradePulse Analytics Service

Python FastAPI service that owns analytics storage and serves ML signals.

## What it does
- Stores analytics data in `analytics-service-db` only
- Keeps a `stocks` replica synced from stock-service (`GET /stocks`) on every analytics sync run
- Fetches grouped daily OHLC from Massive API
- Computes `stock_metrics` + `ml_weekly_features` via PySpark
- Trains using `ml_weekly_features` (technical feature set + `label`)
- Fetches latest Massive news and stores it in `stock_metrics.latest_news`
- Trains and serves a buy/sell signal model (logistic regression, random forest, gradient boosting, xgboost, knn, svm)
- Precomputes and stores per-stock prediction snapshots in `stock_metrics` (`prediction_action`, probabilities, `prediction_confidence_edge`, `prediction_probability_gap`, `prediction_decision_threshold`, reasoning/version metadata), then serves them directly via API
- Retrains model on startup/schedule (configurable)
- Runs freshness-driven OHLC catch-up checks at `05:00` ET, retries every 30 minutes when provider data is not available yet, and performs startup catch-up if the host was offline overnight
- Enforces strict downstream updates: metrics/weekly refresh runs only when OHLC rows changed, and model retraining triggers immediately when weekly features are refreshed
- Backfills `stock_daily_ohlc.return_1d` before metrics refresh so dependent volatility/features are computed from complete OHLC dependency data
- Refreshes prediction snapshot columns only when required `stock_metrics` feature columns are fully populated

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
- `ML_MAX_TRAINING_STOCKS`
- `ML_TRAIN_ON_STARTUP`
- `ML_RETRAIN_INTERVAL_HOURS`
- `STOCK_SERVICE_BASE_URL` (default: `http://stock-service:4003`)
- `STOCK_SERVICE_TIMEOUT_SECONDS` (default: `30`)
- `MASSIVE_API_BASE_URL` (default: `https://api.massive.com`)
- `MASSIVE_API_KEY` (required for OHLC ingestion)
- `MASSIVE_NEWS_LIMIT` (default: `5`)
- `OHLC_YEARS_BACK` (default: `3`)
- `OHLC_RETENTION_BUFFER_DAYS` (default: `90`)
- `OHLC_ADJUSTED` (default: `true`)
- `OHLC_INCLUDE_OTC` (default: `false`)
- `FRESHNESS_CHECK_ENABLED` (default: `true`)
- `FRESHNESS_STARTUP_CATCHUP_ENABLED` (default: `true`)
- `FRESHNESS_POLL_INTERVAL_MINUTES` (default: `30`)
- `FRESHNESS_MORNING_HOUR_ET` (default: `5`)
- `FRESHNESS_MORNING_MINUTE_ET` (default: `0`)
- `FRESHNESS_TIMEZONE` (default: `America/New_York`)

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
