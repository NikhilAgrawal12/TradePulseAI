from __future__ import annotations

from datetime import datetime, timezone
from types import SimpleNamespace

import pandas as pd
import app.main as main
from fastapi import HTTPException


def _reset_state() -> None:
    main.state.update(
        {
            "estimator": None,
            "model_name": None,
            "model_version": None,
            "horizon_days": None,
            "decision_threshold": 0.55,
            "training_status": "pending",
            "training_error": None,
            "last_trained_at": None,
            "freshness_status": "unknown",
            "last_successful_trading_date": None,
            "expected_trading_date": None,
            "last_provider_check_at": None,
            "next_retry_at": None,
            "next_morning_run_at": None,
            "last_sync_trigger": None,
        }
    )


def test_startup_survives_missing_training_data(monkeypatch) -> None:
    _reset_state()

    monkeypatch.setattr(main, "settings", SimpleNamespace(
        train_on_startup=True,
        retrain_interval_hours=0,
        default_days_back=365,
        default_horizon_days=5,
    ))
    monkeypatch.setattr(main.repository, "initialize_tables", lambda: None)
    monkeypatch.setattr(main, "_load_model_from_disk", lambda: False)
    monkeypatch.setattr(main, "_run_startup_training", lambda: None)

    created_threads: list[object] = []

    class FakeThread:
        def __init__(self, target=None, daemon=None, name=None):
            self.target = target
            self.daemon = daemon
            self.name = name
            created_threads.append(self)

        def start(self):
            return None

    monkeypatch.setattr(main, "Thread", FakeThread)

    main.startup()

    health = main.health()
    assert health["status"] == "up"
    assert health["model_loaded"] is False
    assert health["training_status"] == "training"
    assert health["training_error"] is None
    assert len(created_threads) == 1


def test_startup_background_training_updates_status(monkeypatch) -> None:
    _reset_state()

    monkeypatch.setattr(main, "settings", SimpleNamespace(
        train_on_startup=True,
        retrain_interval_hours=0,
        default_days_back=365,
        default_horizon_days=5,
        max_training_stocks=100,
    ))
    monkeypatch.setattr(main.repository, "initialize_tables", lambda: None)
    monkeypatch.setattr(main, "_load_model_from_disk", lambda: False)
    monkeypatch.setattr(main, "_persist_trained_model", lambda trained: main.state.update(
        {
            "estimator": trained.estimator,
            "model_name": trained.selected_model,
            "model_version": trained.model_version,
            "horizon_days": trained.horizon_days,
            "training_status": "trained",
            "training_error": None,
            "last_trained_at": "2026-07-06T00:00:00+00:00",
        }
    ))

    class _Trained:
        estimator = object()
        selected_model = "logistic_regression"
        model_version = "v20260706000000"
        horizon_days = 5
        decision_threshold = 0.55
        trained_rows = 1234
        metrics = [{
            "cv_f1": 0.6,
            "test_f1": 0.7,
            "test_balanced_accuracy": 0.65,
            "test_precision": 0.68,
            "test_recall": 0.72,
        }]

    def _train_ok(*_args, **_kwargs):
        main._persist_trained_model(_Trained())
        return _Trained()

    monkeypatch.setattr(main, "_train_model", _train_ok)

    main._run_startup_training()

    health = main.health()
    assert health["model_loaded"] is True
    assert health["training_status"] == "trained"
    assert health["training_error"] is None


def test_startup_background_training_handles_missing_data(monkeypatch) -> None:
    _reset_state()

    monkeypatch.setattr(main, "settings", SimpleNamespace(
        train_on_startup=True,
        retrain_interval_hours=0,
        default_days_back=365,
        default_horizon_days=5,
        max_training_stocks=100,
    ))

    def _raise_no_data(*_args, **_kwargs):
        raise ValueError("No stock rows found for training window.")

    monkeypatch.setattr(main, "_train_model", _raise_no_data)

    main._run_startup_training()

    health = main.health()
    assert health["status"] == "up"
    assert health["model_loaded"] is False
    assert health["training_status"] == "waiting_for_data"
    assert "No stock rows" in health["training_error"]


def test_persist_trained_model_saves_candidate_metrics(monkeypatch) -> None:
    _reset_state()

    captured: dict[str, object] = {}

    monkeypatch.setattr(main, "_save_model_to_disk", lambda payload: None)
    monkeypatch.setattr(main.repository, "save_model_registry", lambda payload: captured.setdefault("registry", payload))
    monkeypatch.setattr(
        main.repository,
        "save_model_candidates",
        lambda model_version, metrics, selected_model: captured.setdefault(
            "candidates",
            {
                "model_version": model_version,
                "metrics": metrics,
                "selected_model": selected_model,
            },
        ),
    )

    trained = SimpleNamespace(
        estimator=object(),
        selected_model="logistic_regression",
        model_version="v20260707010101",
        horizon_days=5,
        decision_threshold=0.55,
        trained_rows=1234,
        metrics=[
             {
                 "model_name": "logistic_regression",
                 "cv_f1": 0.6,
                 "test_f1": 0.7,
                 "test_balanced_accuracy": 0.65,
                 "test_precision": 0.66,
                 "test_recall": 0.64,
             },
             {
                 "model_name": "xgboost",
                 "cv_f1": 0.58,
                 "test_f1": 0.61,
                 "test_balanced_accuracy": 0.6,
                 "test_precision": 0.62,
                 "test_recall": 0.59,
             },
        ],
    )

    main._persist_trained_model(trained)

    assert "registry" in captured
    assert "candidates" in captured
    candidate_payload = captured["candidates"]
    assert isinstance(candidate_payload, dict)
    assert candidate_payload["model_version"] == "v20260707010101"
    assert candidate_payload["selected_model"] == "logistic_regression"
    assert len(candidate_payload["metrics"]) == 2


def test_load_model_from_disk_rejects_incompatible_feature_set(monkeypatch) -> None:
    _reset_state()

    class FakePath:
        def __init__(self, *_args, **_kwargs):
            pass

        def exists(self) -> bool:
            return True

    monkeypatch.setattr(main, "Path", FakePath)
    monkeypatch.setattr(
        main.joblib,
        "load",
        lambda _path: {
            "estimator": object(),
            "model_name": "xgboost",
            "model_version": "v20260718000000",
            "horizon_days": 5,
            "decision_threshold": 0.55,
            "feature_names": [*main.NUMERIC_FEATURES, "news_count"],
        },
    )

    loaded = main._load_model_from_disk()

    assert loaded is False
    assert main.state["estimator"] is None
    assert main.state["training_status"] == "artifact_incompatible"
    assert "Saved model features do not match current ML feature set" in str(main.state["training_error"])


def test_startup_retrains_when_saved_artifact_is_incompatible(monkeypatch) -> None:
    _reset_state()

    monkeypatch.setattr(main, "settings", SimpleNamespace(
        train_on_startup=True,
        retrain_interval_hours=0,
        default_days_back=365,
        default_horizon_days=5,
        max_training_stocks=100,
        model_path="/tmp/tradepulse_model.joblib",
    ))
    monkeypatch.setattr(main.repository, "initialize_tables", lambda: None)
    monkeypatch.setattr(main, "_load_model_from_disk", lambda: False)

    created_threads: list[object] = []

    class FakeThread:
        def __init__(self, target=None, daemon=None, name=None):
            self.target = target
            self.daemon = daemon
            self.name = name
            created_threads.append(self)

        def start(self):
            return None

    monkeypatch.setattr(main, "Thread", FakeThread)

    main.startup()

    assert main.state["training_status"] == "training"
    assert len(created_threads) == 1


def test_get_prediction_reads_snapshot_only(monkeypatch) -> None:
    _reset_state()
    main.state.update(
        {
            "estimator": object(),
            "model_name": "logistic_regression",
            "model_version": "v20260806000000",
            "horizon_days": 5,
        }
    )

    monkeypatch.setattr(main, "_load_model_from_disk", lambda: True)
    monkeypatch.setattr(
        main.repository,
        "fetch_latest_stock_row",
        lambda stock_id: pd.DataFrame([{"stock_id": stock_id, "symbol": "AAPL"}]),
    )
    monkeypatch.setattr(
        main.repository,
        "fetch_prediction_snapshot",
        lambda stock_id, model_version: {
            "stock_id": stock_id,
            "symbol": "AAPL",
            "prediction_action": "BUY",
            "prediction_confidence": 0.84,
            "prediction_probability_buy": 0.84,
            "prediction_probability_sell": 0.16,
            "prediction_confidence_edge": 0.68,
            "prediction_probability_gap": 0.68,
            "prediction_conviction_label": "strong",
            "prediction_reasoning": '["momentum", "volume"]',
            "prediction_model_version": model_version,
            "prediction_generated_at": datetime(2026, 8, 6, 0, 0, tzinfo=timezone.utc),
            "prediction_horizon_days": 5,
            "prediction_decision_threshold": 0.55,
        },
    )
    monkeypatch.setattr(
        main.repository,
        "fetch_model_metrics",
        lambda _model_version: {
            "cv_f1": 0.63,
            "test_f1": 0.61,
            "test_balanced_accuracy": 0.62,
            "test_precision": 0.60,
            "test_recall": 0.64,
        },
    )
    monkeypatch.setattr(main, "predict_action", lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("should not run live inference")))

    response = main.get_prediction(stock_id=1)

    assert response.stockId == 1
    assert response.action == "BUY"
    assert response.decisionThreshold == 0.55
    assert response.confidenceEdge == 0.68
    assert response.probabilityGap == 0.68
    assert response.modelVersion == "v20260806000000"
    assert response.reasoning == ["momentum", "volume"]


def test_get_prediction_returns_503_when_snapshot_missing(monkeypatch) -> None:
    _reset_state()
    main.state.update(
        {
            "estimator": object(),
            "model_name": "logistic_regression",
            "model_version": "v20260806000000",
            "horizon_days": 5,
        }
    )

    monkeypatch.setattr(main, "_load_model_from_disk", lambda: True)
    monkeypatch.setattr(
        main.repository,
        "fetch_latest_stock_row",
        lambda stock_id: pd.DataFrame([{"stock_id": stock_id, "symbol": "AAPL"}]),
    )
    monkeypatch.setattr(main.repository, "fetch_prediction_snapshot", lambda stock_id, model_version: None)

    try:
        main.get_prediction(stock_id=1)
        assert False, "Expected HTTPException"
    except HTTPException as error:
        assert error.status_code == 503
        assert "Prediction snapshot unavailable" in str(error.detail)


