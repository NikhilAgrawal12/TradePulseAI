from __future__ import annotations

import logging
from datetime import datetime, timezone
from pathlib import Path
from threading import Event, Lock, Thread
from time import sleep
from typing import Any

import joblib
from fastapi import FastAPI, HTTPException

from app.data import StockDataRepository
from app.ml_pipeline import (
    ACTION_THRESHOLD,
    CATEGORICAL_FEATURES,
    FIXED_RETURN_THRESHOLD,
    NUMERIC_FEATURES,
    build_prediction_row,
    predict_action,
    train_and_select_model,
)
from app.schemas import PredictionResponse, TrainRequest, TrainResponse
from app.settings import settings

app = FastAPI(title="TradePulse ML Service", version="1.0.0")
logger = logging.getLogger(__name__)

repository = StockDataRepository(settings.database_url)
state: dict[str, Any] = {
    "estimator": None,
    "model_name": None,
    "model_version": None,
    "horizon_days": None,
    "positive_return_threshold": None,
    "neutral_return_band": None,
    "decision_threshold": ACTION_THRESHOLD,
    "training_status": "pending",
    "training_error": None,
    "last_trained_at": None,
}
training_lock = Lock()
stop_event = Event()
scheduler_thread: Thread | None = None
startup_training_thread: Thread | None = None


def _expected_feature_names() -> list[str]:
    return [*NUMERIC_FEATURES, *CATEGORICAL_FEATURES]


def _extract_artifact_feature_names(artifact: dict[str, Any]) -> list[str] | None:
    saved_feature_names = artifact.get("feature_names")
    if isinstance(saved_feature_names, list) and all(isinstance(name, str) for name in saved_feature_names):
        return saved_feature_names

    estimator = artifact.get("estimator")
    if estimator is None or not hasattr(estimator, "named_steps"):
        return None

    preprocessor = estimator.named_steps.get("preprocessor")
    feature_names_in = getattr(preprocessor, "feature_names_in_", None)
    if feature_names_in is None:
        return None

    return [str(name) for name in feature_names_in.tolist()]


def _persist_trained_model(trained: Any) -> None:

    artifact = {
        "estimator": trained.estimator,
        "model_name": trained.selected_model,
        "model_version": trained.model_version,
        "horizon_days": trained.horizon_days,
        "positive_return_threshold": trained.positive_return_threshold,
        "neutral_return_band": float(getattr(trained, "neutral_return_band", settings.default_neutral_return_band)),
        "decision_threshold": trained.decision_threshold,
        "feature_names": _expected_feature_names(),
    }
    _save_model_to_disk(artifact)

    state["estimator"] = trained.estimator
    state["model_name"] = trained.selected_model
    state["model_version"] = trained.model_version
    state["horizon_days"] = trained.horizon_days
    state["positive_return_threshold"] = trained.positive_return_threshold
    state["neutral_return_band"] = float(getattr(trained, "neutral_return_band", settings.default_neutral_return_band))
    state["decision_threshold"] = trained.decision_threshold
    state["training_status"] = "trained"
    state["training_error"] = None
    state["last_trained_at"] = datetime.now(timezone.utc).isoformat()

    top_metric = trained.metrics[0]
    repository.save_model_registry(
        {
            "model_version": trained.model_version,
            "model_name": trained.selected_model,
            "horizon_days": trained.horizon_days,
            "positive_return_threshold": trained.positive_return_threshold,
            "decision_threshold": trained.decision_threshold,
            "trained_rows": trained.trained_rows,
            "cv_f1": top_metric["cv_f1"],
            "test_f1": top_metric["test_f1"],
            "test_balanced_accuracy": top_metric["test_balanced_accuracy"],
            "test_precision": top_metric["test_precision"],
            "test_recall": top_metric["test_recall"],
        }
    )
    repository.save_model_candidates(
        model_version=trained.model_version,
        metrics=trained.metrics,
        selected_model=trained.selected_model,
    )


def _train_model(days_back: int, horizon_days: int, positive_return_threshold: float, neutral_return_band: float) -> Any:
    with training_lock:
        training_frame = repository.fetch_training_data(
            days_back=days_back,
            max_training_stocks=settings.max_training_stocks,
        )
        if training_frame.empty:
            raise ValueError("No stock rows found for training window.")

        trained = train_and_select_model(
            frame=training_frame,
            horizon_days=horizon_days,
            positive_return_threshold=positive_return_threshold,
            neutral_return_band=neutral_return_band,
        )
        _persist_trained_model(trained)
        return trained


def _run_startup_training() -> None:
    try:
        _train_model(
            days_back=settings.default_days_back,
            horizon_days=settings.default_horizon_days,
            positive_return_threshold=FIXED_RETURN_THRESHOLD,
            neutral_return_band=FIXED_RETURN_THRESHOLD,
        )
    except ValueError as error:
        state["training_status"] = "waiting_for_data"
        state["training_error"] = str(error)
        logger.warning("ML startup training skipped: %s", error)
    except Exception as error:
        state["training_status"] = "startup_error"
        state["training_error"] = str(error)
        logger.exception("ML startup training failed")


def _run_scheduled_training() -> None:
    interval_seconds = max(settings.retrain_interval_hours, 1) * 3600
    # Wait first interval to avoid duplicate startup training.
    while not stop_event.wait(interval_seconds):
        try:
            _train_model(
                days_back=settings.default_days_back,
                horizon_days=settings.default_horizon_days,
                positive_return_threshold=FIXED_RETURN_THRESHOLD,
                neutral_return_band=FIXED_RETURN_THRESHOLD,
            )
        except Exception:
            # Keep scheduler alive even when one retraining run fails.
            sleep(1)


def _load_model_from_disk() -> bool:
    model_file = Path(settings.model_path)
    if not model_file.exists():
        return False

    artifact = joblib.load(model_file)
    artifact_feature_names = _extract_artifact_feature_names(artifact)
    expected_feature_names = _expected_feature_names()
    if artifact_feature_names != expected_feature_names:
        state["estimator"] = None
        state["model_name"] = None
        state["model_version"] = None
        state["horizon_days"] = None
        state["positive_return_threshold"] = None
        state["neutral_return_band"] = None
        state["decision_threshold"] = ACTION_THRESHOLD
        state["training_status"] = "artifact_incompatible"
        state["training_error"] = (
            "Saved model features do not match current ML feature set. "
            f"Expected {expected_feature_names!r}, got {artifact_feature_names!r}."
        )
        logger.warning("Ignoring incompatible saved ML model at %s", model_file)
        return False

    state["estimator"] = artifact["estimator"]
    state["model_name"] = artifact["model_name"]
    state["model_version"] = artifact["model_version"]
    state["horizon_days"] = artifact["horizon_days"]
    state["positive_return_threshold"] = artifact["positive_return_threshold"]
    state["neutral_return_band"] = float(artifact.get("neutral_return_band", getattr(settings, "default_neutral_return_band", 0.02)))
    state["decision_threshold"] = float(artifact.get("decision_threshold", ACTION_THRESHOLD))
    state["training_status"] = "loaded"
    state["training_error"] = None
    return True


def _save_model_to_disk(payload: dict[str, Any]) -> None:
    model_file = Path(settings.model_path)
    model_file.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(payload, model_file)


@app.on_event("startup")
def startup() -> None:
    repository.initialize_tables()
    loaded = _load_model_from_disk()

    if settings.train_on_startup and not loaded:
        state["training_status"] = "training"
        state["training_error"] = None
        global startup_training_thread
        startup_training_thread = Thread(target=_run_startup_training, daemon=True, name="ml-startup-training")
        startup_training_thread.start()
    elif not loaded:
        state["training_status"] = "waiting_for_training"
        state["training_error"] = None

    global scheduler_thread
    if settings.retrain_interval_hours > 0:
        scheduler_thread = Thread(target=_run_scheduled_training, daemon=True, name="ml-retrain-scheduler")
        scheduler_thread.start()


@app.on_event("shutdown")
def shutdown() -> None:
    stop_event.set()


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "up",
        "model_loaded": state["estimator"] is not None,
        "model_name": state["model_name"],
        "model_version": state["model_version"],
        "positive_return_threshold": state["positive_return_threshold"],
        "neutral_return_band": state["neutral_return_band"],
        "training_status": state["training_status"],
        "training_error": state["training_error"],
        "last_trained_at": state["last_trained_at"],
        "decision_threshold": state["decision_threshold"],
    }


@app.post("/v1/train", response_model=TrainResponse)
def train_model(payload: TrainRequest) -> TrainResponse:
    try:
        trained = _train_model(
            days_back=payload.days_back,
            horizon_days=payload.horizon_days,
            positive_return_threshold=FIXED_RETURN_THRESHOLD,
            neutral_return_band=FIXED_RETURN_THRESHOLD,
        )
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error

    return TrainResponse(
        selected_model=trained.selected_model,
        trained_rows=trained.trained_rows,
        horizon_days=trained.horizon_days,
        positive_return_threshold=trained.positive_return_threshold,
        neutral_return_band=float(getattr(trained, "neutral_return_band", settings.default_neutral_return_band)),
         metrics=[
             {
                 "model_name": row["model_name"],
                 "cv_f1": row["cv_f1"],
                 "test_f1": row["test_f1"],
                 "test_balanced_accuracy": row["test_balanced_accuracy"],
                 "test_precision": row["test_precision"],
                 "test_recall": row["test_recall"],
             }
             for row in trained.metrics
         ],
    )


@app.get("/v1/predictions/{stock_id}", response_model=PredictionResponse)
def get_prediction(stock_id: int) -> PredictionResponse:
    if state["estimator"] is None and not _load_model_from_disk():
        raise HTTPException(status_code=503, detail="Model is not trained. Run startup training or POST /v1/train.")

    history = repository.fetch_latest_stock_row(stock_id=stock_id)
    if history.empty:
        raise HTTPException(status_code=404, detail=f"No historical rows found for stock_id={stock_id}")

    prediction_row = build_prediction_row(history)
    model_metrics = repository.fetch_model_metrics(str(state["model_version"])) if state["model_version"] else None
    signal = predict_action(
        estimator=state["estimator"],
        prediction_row=prediction_row,
        horizon_days=int(state["horizon_days"]),
        decision_threshold=float(state.get("decision_threshold", ACTION_THRESHOLD)),
    )

    return PredictionResponse(
        stockId=stock_id,
        symbol=str(prediction_row.iloc[0]["symbol"]),
        action=signal["action"],
        confidence=signal["confidence"],
        probabilityBuy=signal["probability_buy"],
        probabilitySell=signal["probability_sell"],
        horizonDays=int(state["horizon_days"]),
        modelName=str(state["model_name"]),
        modelVersion=str(state["model_version"]),
        generatedAt=datetime.now(timezone.utc).isoformat(),
        reasoning=signal["reasoning"],
        convictionLabel=signal["conviction_label"],
        cvF1=(model_metrics["cv_f1"] if model_metrics else None),
        testF1=(model_metrics["test_f1"] if model_metrics else None),
        testBalancedAccuracy=(model_metrics["test_balanced_accuracy"] if model_metrics else None),
        testPrecision=(model_metrics["test_precision"] if model_metrics else None),
        testRecall=(model_metrics["test_recall"] if model_metrics else None),
    )



