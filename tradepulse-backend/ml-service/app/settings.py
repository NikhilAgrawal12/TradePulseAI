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
        "postgresql+psycopg2://postgres:postgres@stock-service-db:5432/tradepulse",
    )
    model_path: str = os.getenv("ML_MODEL_PATH", "/ml-model/tradepulse_model.joblib")
    service_port: int = int(os.getenv("ML_SERVICE_PORT", "4010"))
    default_days_back: int = int(os.getenv("ML_DEFAULT_DAYS_BACK", "730"))
    default_horizon_days: int = int(os.getenv("ML_DEFAULT_HORIZON_DAYS", "5"))
    default_positive_return_threshold: float = float(os.getenv("ML_DEFAULT_POSITIVE_RETURN_THRESHOLD", "0.015"))
    default_neutral_return_band: float = float(os.getenv("ML_DEFAULT_NEUTRAL_RETURN_BAND", "0.015"))
    max_training_stocks: int = int(os.getenv("ML_MAX_TRAINING_STOCKS", "100"))
    max_training_rows: int = int(os.getenv("ML_MAX_TRAINING_ROWS", "100000"))
    train_on_startup: bool = _to_bool(os.getenv("ML_TRAIN_ON_STARTUP"), True)
    retrain_interval_hours: int = int(os.getenv("ML_RETRAIN_INTERVAL_HOURS", "168"))


settings = Settings()

