import os
from dataclasses import dataclass


def _to_bool(value: str | None, default: bool) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    database_url: str = os.getenv(
        "ML_DATABASE_URL",
        "postgresql+psycopg2://postgres:postgres@analytics-service-db:5432/tradepulse",
    )
    model_path: str = os.getenv("ML_MODEL_PATH", "/ml-model/tradepulse_model.joblib")
    service_port: int = int(os.getenv("ML_SERVICE_PORT", "4010"))
    default_days_back: int = int(os.getenv("ML_DEFAULT_DAYS_BACK", "365"))
    default_horizon_days: int = int(os.getenv("ML_DEFAULT_HORIZON_DAYS", "5"))
    max_training_stocks: int = int(os.getenv("ML_MAX_TRAINING_STOCKS", "100"))
    train_on_startup: bool = _to_bool(os.getenv("ML_TRAIN_ON_STARTUP"), True)
    retrain_interval_hours: int = int(os.getenv("ML_RETRAIN_INTERVAL_HOURS", "168"))

    # Nightly analytics pipeline settings
    stock_service_base_url: str = os.getenv("STOCK_SERVICE_BASE_URL", "http://stock-service:4003")
    stock_service_timeout_seconds: int = int(os.getenv("STOCK_SERVICE_TIMEOUT_SECONDS", "30"))
    stock_replica_sync_enabled: bool = _to_bool(os.getenv("STOCK_REPLICA_SYNC_ENABLED"), False)
    massive_api_base_url: str = os.getenv("MASSIVE_API_BASE_URL", "https://api.massive.com")
    massive_api_key: str = os.getenv("MASSIVE_API_KEY", "")
    massive_news_limit: int = int(os.getenv("MASSIVE_NEWS_LIMIT", "5"))
    ohlc_years_back: int = int(os.getenv("OHLC_YEARS_BACK", "3"))
    ohlc_retention_buffer_days: int = int(os.getenv("OHLC_RETENTION_BUFFER_DAYS", "90"))
    ohlc_adjusted: bool = _to_bool(os.getenv("OHLC_ADJUSTED"), True)
    ohlc_include_otc: bool = _to_bool(os.getenv("OHLC_INCLUDE_OTC"), False)
    nightly_sync_enabled: bool = _to_bool(os.getenv("NIGHTLY_SYNC_ENABLED"), True)
    nightly_sync_on_startup: bool = _to_bool(os.getenv("NIGHTLY_SYNC_ON_STARTUP"), True)
    nightly_sync_interval_hours: int = int(os.getenv("NIGHTLY_SYNC_INTERVAL_HOURS", "24"))
    freshness_check_enabled: bool = _to_bool(os.getenv("FRESHNESS_CHECK_ENABLED"), True)
    freshness_startup_catchup_enabled: bool = _to_bool(os.getenv("FRESHNESS_STARTUP_CATCHUP_ENABLED"), True)
    freshness_poll_interval_minutes: int = int(os.getenv("FRESHNESS_POLL_INTERVAL_MINUTES", "30"))
    freshness_morning_hour_et: int = int(os.getenv("FRESHNESS_MORNING_HOUR_ET", "5"))
    freshness_morning_minute_et: int = int(os.getenv("FRESHNESS_MORNING_MINUTE_ET", "0"))
    freshness_timezone: str = os.getenv("FRESHNESS_TIMEZONE", "America/New_York")


settings = Settings()
