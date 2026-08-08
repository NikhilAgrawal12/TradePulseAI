from __future__ import annotations

import json
import logging
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path
from threading import Event, Lock, Thread
from time import sleep
from typing import Any
from zoneinfo import ZoneInfo

import joblib
from fastapi import FastAPI, HTTPException, Query

from app.analytics_sync import AnalyticsSyncService
from app.data import StockDataRepository
from app.ml_pipeline import (
    ACTION_THRESHOLD,
    CATEGORICAL_FEATURES,
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
analytics_sync_service = AnalyticsSyncService(repository, settings)
state: dict[str, Any] = {
    "estimator": None,
    "model_name": None,
    "model_version": None,
    "horizon_days": None,
    "decision_threshold": ACTION_THRESHOLD,
    "training_status": "pending",
    "training_error": None,
    "last_trained_at": None,
    "last_sync_status": "never",
    "last_sync_error": None,
    "last_sync_finished_at": None,
    "last_sync_stats": None,
    "freshness_status": "unknown",
    "last_successful_trading_date": None,
    "expected_trading_date": None,
    "last_provider_check_at": None,
    "next_retry_at": None,
    "next_morning_run_at": None,
    "last_sync_trigger": None,
}
training_lock = Lock()
sync_lock = Lock()
stop_event = Event()
scheduler_thread: Thread | None = None
startup_training_thread: Thread | None = None
sync_scheduler_thread: Thread | None = None
startup_sync_thread: Thread | None = None
freshness_scheduler_thread: Thread | None = None
freshness_startup_thread: Thread | None = None


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
    now_iso = datetime.now(timezone.utc).isoformat()
    artifact = {
        "estimator": trained.estimator,
        "model_name": trained.selected_model,
        "model_version": trained.model_version,
        "horizon_days": trained.horizon_days,
        "decision_threshold": trained.decision_threshold,
        "feature_names": _expected_feature_names(),
        "trained_at": now_iso,
    }
    _save_model_to_disk(artifact)

    state["estimator"] = trained.estimator
    state["model_name"] = trained.selected_model
    state["model_version"] = trained.model_version
    state["horizon_days"] = trained.horizon_days
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
    try:
        _refresh_prediction_cache()
    except Exception:
        logger.exception("Failed to refresh prediction cache after training")


def _refresh_prediction_cache() -> int:
    if state["estimator"] is None or state.get("model_version") is None or state.get("horizon_days") is None:
        return 0

    completeness = repository.fetch_prediction_metrics_completeness()
    if completeness["total_rows"] == 0:
        logger.warning("Skipping prediction snapshot refresh because stock_metrics has no rows.")
        return 0
    if completeness["incomplete_rows"] > 0:
        logger.warning(
            "Skipping prediction snapshot refresh because stock_metrics has %d incomplete rows.",
            completeness["incomplete_rows"],
        )
        return 0

    feature_rows = repository.fetch_prediction_feature_rows()
    if feature_rows.empty:
        return 0

    prediction_rows: list[dict[str, Any]] = []
    for _, source_row in feature_rows.iterrows():
        prediction_input = build_prediction_row(source_row.to_frame().T)
        signal = predict_action(
            estimator=state["estimator"],
            prediction_row=prediction_input,
            horizon_days=int(state["horizon_days"]),
            decision_threshold=float(state.get("decision_threshold", ACTION_THRESHOLD)),
        )
        prediction_rows.append(
            {
                "stock_id": int(source_row["stock_id"]),
                "prediction_action": str(signal["action"]),
                "prediction_confidence": float(signal["confidence"]),
                "prediction_probability_buy": float(signal["probability_buy"]),
                "prediction_probability_sell": float(signal["probability_sell"]),
                "prediction_confidence_edge": float(signal["confidence_edge"]),
                "prediction_probability_gap": float(signal["probability_gap"]),
                "prediction_conviction_label": str(signal["conviction_label"]),
                "prediction_reasoning": json.dumps(signal["reasoning"]),
                "prediction_model_version": str(state["model_version"]),
                "prediction_horizon_days": int(state["horizon_days"]),
                "prediction_decision_threshold": float(state.get("decision_threshold", ACTION_THRESHOLD)),
                "prediction_generated_at": datetime.now(timezone.utc),
            }
        )

    return repository.store_prediction_snapshots(prediction_rows)


def _train_model(days_back: int, horizon_days: int) -> Any:
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
        )
        _persist_trained_model(trained)
        return trained


def _run_startup_training() -> None:
    try:
        _train_model(
            days_back=settings.default_days_back,
            horizon_days=settings.default_horizon_days,
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
    # (Startup already handled overdue retraining, so we always wait a full interval here.)
    while not stop_event.wait(interval_seconds):
        try:
            _train_model(
                days_back=settings.default_days_back,
                horizon_days=settings.default_horizon_days,
            )
        except Exception:
            # Keep scheduler alive even when one retraining run fails.
            sleep(1)


def _get_freshness_timezone() -> ZoneInfo:
    timezone_name = str(getattr(settings, "freshness_timezone", "America/New_York") or "America/New_York")
    try:
        return ZoneInfo(timezone_name)
    except Exception:
        logger.warning("Invalid freshness timezone %r. Falling back to America/New_York.", timezone_name)
        return ZoneInfo("America/New_York")


def _next_morning_run_at_utc(now_utc: datetime | None = None) -> datetime:
    now_utc = now_utc or datetime.now(timezone.utc)
    tz = _get_freshness_timezone()
    now_local = now_utc.astimezone(tz)
    run_local = datetime.combine(
        now_local.date(),
        time(
            hour=max(0, min(int(getattr(settings, "freshness_morning_hour_et", 5)), 23)),
            minute=max(0, min(int(getattr(settings, "freshness_morning_minute_et", 0)), 59)),
        ),
        tzinfo=tz,
    )
    if now_local >= run_local:
        run_local = run_local + timedelta(days=1)
    return run_local.astimezone(timezone.utc)


def _target_trading_date(now_utc: datetime | None = None) -> date:
    now_utc = now_utc or datetime.now(timezone.utc)
    local_day = now_utc.astimezone(_get_freshness_timezone()).date()
    if local_day.weekday() < 5:
        return local_day

    cursor = local_day
    while cursor.weekday() >= 5:
        cursor -= timedelta(days=1)
    return cursor


def _schedule_next_retry(reference_utc: datetime | None = None) -> datetime:
    reference_utc = reference_utc or datetime.now(timezone.utc)
    poll_minutes = max(1, int(getattr(settings, "freshness_poll_interval_minutes", 30)))
    return reference_utc + timedelta(minutes=poll_minutes)


def _run_analytics_sync(trigger: str) -> dict[str, Any]:
    with sync_lock:
        try:
            state["last_sync_status"] = "running"
            state["last_sync_error"] = None
            state["last_sync_trigger"] = trigger
            stats = analytics_sync_service.run_pipeline(trigger=trigger)
            payload = {
                "trigger": stats.trigger,
                "synced_stocks": stats.synced_stocks,
                "inserted_or_updated_ohlc_rows": stats.inserted_or_updated_ohlc_rows,
                "ohlc_dependency_updates": stats.ohlc_dependency_updates,
                "ohlc_start_date": None if stats.ohlc_start_date is None else stats.ohlc_start_date.isoformat(),
                "ohlc_end_date": None if stats.ohlc_end_date is None else stats.ohlc_end_date.isoformat(),
                "metrics_rows": stats.metrics_rows,
                "weekly_feature_rows": stats.weekly_feature_rows,
                "finished_at": stats.finished_at,
                "metrics_completeness": repository.fetch_prediction_metrics_completeness(),
            }
            payload["model_retrained"] = False
            payload["prediction_rows"] = 0

            if stats.weekly_feature_rows > 0:
                try:
                    trained = _train_model(
                        days_back=settings.default_days_back,
                        horizon_days=settings.default_horizon_days,
                    )
                    payload["model_retrained"] = True
                    payload["model_version"] = trained.model_version
                    payload["selected_model"] = trained.selected_model
                except ValueError as error:
                    logger.warning("Skipping post-sync retraining: %s", error)
                except Exception:
                    logger.exception("Post-sync retraining failed")
            elif stats.metrics_rows > 0 and state["estimator"] is not None:
                try:
                    payload["prediction_rows"] = _refresh_prediction_cache()
                except Exception:
                    logger.exception("Failed to refresh prediction cache after metrics refresh")
                    payload["prediction_rows"] = 0
            state["last_sync_status"] = "ok"
            state["last_sync_finished_at"] = stats.finished_at
            state["last_sync_stats"] = payload
            state["freshness_status"] = "fresh"
            latest = analytics_sync_service.get_latest_ohlc_trading_date()
            state["last_successful_trading_date"] = latest.isoformat() if latest else None
            state["next_retry_at"] = None
            return payload
        except Exception as error:  # pragma: no cover - external services/db dependency
            state["last_sync_status"] = "failed"
            state["last_sync_error"] = str(error)
            state["last_sync_finished_at"] = datetime.now(timezone.utc).isoformat()
            state["freshness_status"] = "failed"
            raise


def _check_freshness_and_sync(trigger: str) -> None:
    now_utc = datetime.now(timezone.utc)
    state["last_sync_trigger"] = trigger
    expected_trading_date = _target_trading_date(now_utc)
    state["expected_trading_date"] = expected_trading_date.isoformat()

    latest_db_date = analytics_sync_service.get_latest_ohlc_trading_date()
    state["last_successful_trading_date"] = latest_db_date.isoformat() if latest_db_date else None

    if latest_db_date is not None and latest_db_date >= expected_trading_date:
        state["freshness_status"] = "fresh"
        state["next_retry_at"] = None
        return

    state["freshness_status"] = "stale"
    state["last_provider_check_at"] = now_utc.isoformat()
    provider_has_data = analytics_sync_service.is_provider_ohlc_available_for_date(expected_trading_date)

    if not provider_has_data:
        next_retry = _schedule_next_retry(now_utc)
        state["freshness_status"] = "waiting_provider"
        state["next_retry_at"] = next_retry.isoformat()
        return

    try:
        _run_analytics_sync(trigger=trigger)
    except Exception:
        next_retry = _schedule_next_retry(datetime.now(timezone.utc))
        state["next_retry_at"] = next_retry.isoformat()


def _run_freshness_scheduler() -> None:
    while not stop_event.is_set():
        now_utc = datetime.now(timezone.utc)

        next_retry_at_iso = state.get("next_retry_at")
        next_retry_at: datetime | None = None
        if isinstance(next_retry_at_iso, str):
            try:
                next_retry_at = datetime.fromisoformat(next_retry_at_iso)
            except Exception:
                next_retry_at = None

        next_morning_run_at_iso = state.get("next_morning_run_at")
        next_morning_run_at: datetime | None = None
        if isinstance(next_morning_run_at_iso, str):
            try:
                next_morning_run_at = datetime.fromisoformat(next_morning_run_at_iso)
            except Exception:
                next_morning_run_at = None
        if next_morning_run_at is None:
            next_morning_run_at = _next_morning_run_at_utc(now_utc)
            state["next_morning_run_at"] = next_morning_run_at.isoformat()

        should_run_retry = next_retry_at is not None and now_utc >= next_retry_at
        should_run_morning = next_morning_run_at is not None and now_utc >= next_morning_run_at
        if should_run_retry:
            try:
                _check_freshness_and_sync(trigger="retry_30m")
            except Exception:
                logger.exception("Freshness retry loop failed")
                state["next_retry_at"] = _schedule_next_retry(datetime.now(timezone.utc)).isoformat()
            continue

        if should_run_morning:
            try:
                _check_freshness_and_sync(trigger="scheduled_5am")
            except Exception:
                logger.exception("Scheduled 5 AM freshness sync failed")
                state["next_retry_at"] = _schedule_next_retry(datetime.now(timezone.utc)).isoformat()
            finally:
                state["next_morning_run_at"] = _next_morning_run_at_utc(datetime.now(timezone.utc)).isoformat()
            continue

        # Wake up frequently enough to honor retry timers while staying lightweight.
        stop_event.wait(30)


def _run_scheduled_analytics_sync() -> None:
    interval_hours = int(getattr(settings, "nightly_sync_interval_hours", 24))
    interval_seconds = max(interval_hours, 1) * 3600
    while not stop_event.wait(interval_seconds):
        try:
            _run_analytics_sync(trigger="scheduled")
        except Exception:
            sleep(1)


def _run_startup_analytics_sync() -> None:
    try:
        _run_analytics_sync(trigger="startup")
    except Exception:
        # Keep service available even if one startup catch-up run fails.
        pass


def _run_startup_freshness_check() -> None:
    try:
        _check_freshness_and_sync(trigger="startup_catchup")
    except Exception:
        logger.exception("Startup freshness catch-up check failed")


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
    state["decision_threshold"] = float(artifact.get("decision_threshold", ACTION_THRESHOLD))
    state["training_status"] = "loaded"
    state["training_error"] = None
    # Restore last_trained_at from artifact (preferred) or file mtime as fallback.
    trained_at = artifact.get("trained_at")
    if trained_at:
        state["last_trained_at"] = trained_at
    else:
        mtime = model_file.stat().st_mtime
        state["last_trained_at"] = datetime.fromtimestamp(mtime, tz=timezone.utc).isoformat()
    return True


def _save_model_to_disk(payload: dict[str, Any]) -> None:
    model_file = Path(settings.model_path)
    model_file.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(payload, model_file)


@app.on_event("startup")
def startup() -> None:
    repository.initialize_tables()

    freshness_enabled = bool(getattr(settings, "freshness_check_enabled", False))

    if (
        not freshness_enabled
        and bool(getattr(settings, "nightly_sync_enabled", False))
        and bool(getattr(settings, "nightly_sync_on_startup", False))
    ):
        global startup_sync_thread
        startup_sync_thread = Thread(target=_run_startup_analytics_sync, daemon=True, name="analytics-sync-startup")
        startup_sync_thread.start()

    if freshness_enabled and bool(getattr(settings, "freshness_startup_catchup_enabled", False)):
        global freshness_startup_thread
        freshness_startup_thread = Thread(target=_run_startup_freshness_check, daemon=True, name="analytics-freshness-startup")
        freshness_startup_thread.start()

    loaded = _load_model_from_disk()

    # Determine if we need to (re)train on startup.
    # Retrain if: no model loaded, OR model is older than retrain_interval_hours (missed schedule while off).
    needs_training = not loaded
    if loaded and settings.retrain_interval_hours > 0 and state.get("last_trained_at"):
        try:
            last_trained = datetime.fromisoformat(state["last_trained_at"])
            age_hours = (datetime.now(timezone.utc) - last_trained).total_seconds() / 3600
            if age_hours >= settings.retrain_interval_hours:
                logger.info(
                    "ML model is %.1f hours old (interval=%dh) — retraining on startup.",
                    age_hours,
                    settings.retrain_interval_hours,
                )
                needs_training = True
        except Exception:
            pass  # If we can't parse the date, don't force retrain

    if settings.train_on_startup and needs_training:
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

    global sync_scheduler_thread
    if bool(getattr(settings, "nightly_sync_enabled", False)):
        sync_scheduler_thread = Thread(target=_run_scheduled_analytics_sync, daemon=True, name="analytics-sync-scheduler")
        sync_scheduler_thread.start()

    global freshness_scheduler_thread
    if freshness_enabled:
        state["next_morning_run_at"] = _next_morning_run_at_utc(datetime.now(timezone.utc)).isoformat()
        freshness_scheduler_thread = Thread(target=_run_freshness_scheduler, daemon=True, name="analytics-freshness-scheduler")
        freshness_scheduler_thread.start()


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
        "training_status": state["training_status"],
        "training_error": state["training_error"],
        "last_trained_at": state["last_trained_at"],
        "decision_threshold": state["decision_threshold"],
        "last_sync_status": state["last_sync_status"],
        "last_sync_error": state["last_sync_error"],
        "last_sync_finished_at": state["last_sync_finished_at"],
        "freshness_status": state["freshness_status"],
        "last_successful_trading_date": state["last_successful_trading_date"],
        "expected_trading_date": state["expected_trading_date"],
        "last_provider_check_at": state["last_provider_check_at"],
        "next_retry_at": state["next_retry_at"],
        "next_morning_run_at": state["next_morning_run_at"],
    }


@app.post("/v1/admin/sync-nightly")
def trigger_nightly_sync() -> dict[str, Any]:
    try:
        payload = _run_analytics_sync(trigger="manual")
        return {"accepted": True, "result": payload}
    except Exception as error:
        raise HTTPException(status_code=500, detail=f"Nightly sync failed: {error}") from error


@app.get("/v1/admin/sync-status")
def get_sync_status() -> dict[str, Any]:
    return {
        "status": state["last_sync_status"],
        "freshness_status": state["freshness_status"],
        "error": state["last_sync_error"],
        "finished_at": state["last_sync_finished_at"],
        "last_successful_trading_date": state["last_successful_trading_date"],
        "expected_trading_date": state["expected_trading_date"],
        "last_provider_check_at": state["last_provider_check_at"],
        "next_retry_at": state["next_retry_at"],
        "next_morning_run_at": state["next_morning_run_at"],
        "last_sync_trigger": state["last_sync_trigger"],
        "stats": state["last_sync_stats"],
    }


@app.get("/v1/analytics/stocks/{stock_id}/insights")
def get_stock_insights(stock_id: int) -> dict[str, Any]:
    payload = repository.fetch_stock_insights_payload(stock_id=stock_id)
    if payload is None:
        raise HTTPException(status_code=404, detail=f"Stock not found for stock_id={stock_id}")
    return payload


@app.get("/v1/analytics/news")
def get_analytics_news(limit: int = Query(default=10, ge=1, le=100)) -> list[dict[str, Any]]:
    return repository.fetch_analytics_news(limit=limit)


@app.post("/v1/train", response_model=TrainResponse)
def train_model(payload: TrainRequest) -> TrainResponse:
    try:
        trained = _train_model(
            days_back=payload.days_back,
            horizon_days=payload.horizon_days,
        )
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error

    return TrainResponse(
        selected_model=trained.selected_model,
        trained_rows=trained.trained_rows,
        horizon_days=trained.horizon_days,
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

    cached = repository.fetch_prediction_snapshot(stock_id=stock_id, model_version=str(state["model_version"]))
    if cached is None:
        raise HTTPException(
            status_code=503,
            detail="Prediction snapshot unavailable. Run sync/training to populate stock_metrics.",
        )

    model_metrics = repository.fetch_model_metrics(str(state["model_version"])) if state["model_version"] else None
    reasoning_raw = cached.get("prediction_reasoning")
    try:
        reasoning = json.loads(reasoning_raw) if isinstance(reasoning_raw, str) else []
        if not isinstance(reasoning, list):
            reasoning = []
    except Exception:
        reasoning = []

    return PredictionResponse(
        stockId=stock_id,
        symbol=str(cached.get("symbol") or history.iloc[0].get("symbol")),
        action=str(cached["prediction_action"]),
        confidence=float(cached["prediction_confidence"]),
        probabilityBuy=float(cached["prediction_probability_buy"]),
        probabilitySell=float(cached["prediction_probability_sell"]),
        confidenceEdge=(
            float(cached["prediction_confidence_edge"])
            if cached.get("prediction_confidence_edge") is not None
            else None
        ),
        probabilityGap=(
            float(cached["prediction_probability_gap"])
            if cached.get("prediction_probability_gap") is not None
            else None
        ),
        decisionThreshold=(
            float(cached["prediction_decision_threshold"])
            if cached.get("prediction_decision_threshold") is not None
            else float(state.get("decision_threshold", ACTION_THRESHOLD))
        ),
        horizonDays=int(cached.get("prediction_horizon_days") or state["horizon_days"]),
        modelName=str(state["model_name"]),
        modelVersion=str(state["model_version"]),
        generatedAt=(
            cached["prediction_generated_at"].isoformat()
            if hasattr(cached.get("prediction_generated_at"), "isoformat")
            else datetime.now(timezone.utc).isoformat()
        ),
        reasoning=[str(item) for item in reasoning],
        convictionLabel=str(cached["prediction_conviction_label"]),
        cvF1=(model_metrics["cv_f1"] if model_metrics else None),
        testF1=(model_metrics["test_f1"] if model_metrics else None),
        testBalancedAccuracy=(model_metrics["test_balanced_accuracy"] if model_metrics else None),
        testPrecision=(model_metrics["test_precision"] if model_metrics else None),
        testRecall=(model_metrics["test_recall"] if model_metrics else None),
    )



