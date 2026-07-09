# TradePulseAI ML Service

Python FastAPI service that trains and serves a buy/sell signal model for stocks.

## What it does
- Loads OHLC + technical features from `stock-service-db`
- Builds additional lag/rolling features
- Applies preprocessing (imputation, one-hot encoding, robust scaling)
- Applies feature selection and dimensionality reduction
- Handles class imbalance with over/under sampling
- Runs model selection with time-series-aware cross validation
- Saves model artifact and serves predictions
- Trains a default model on service startup when no saved model is available
- Retrains automatically on a configurable schedule (disabled when interval is 0)
- Uses a smaller default training window and caps the number of stocks/rows to keep startup memory usage manageable

## API
- `GET /health`
- `POST /v1/train`
- `GET /v1/predictions/{stock_id}`

## Environment variables
- `ML_DATABASE_URL` (default: `postgresql+psycopg2://postgres:postgres@stock-service-db:5432/tradepulse`)
- `ML_MODEL_PATH` (default: `/ml-model/tradepulse_model.joblib`)
- `ML_SERVICE_PORT` (default: `4010`)
- `ML_DEFAULT_DAYS_BACK` (default: `365`)
- `ML_DEFAULT_HORIZON_DAYS` (default: `5`)
- `ML_DEFAULT_POSITIVE_RETURN_THRESHOLD` (default: `0.02`)
- `ML_DEFAULT_NEUTRAL_RETURN_BAND` (default: `0.01`)
- `ML_MAX_TRAINING_STOCKS` (default: `100`)
- `ML_MAX_TRAINING_ROWS` (default: `30000`)
- `ML_TRAIN_ON_STARTUP` (default: `true`)
- `ML_RETRAIN_INTERVAL_HOURS` (default: `168`)

## Local run
```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 4010
```

## Run tests
```bash
pytest -q
```


