from __future__ import annotations

from datetime import date, datetime, timezone
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
            "last_sync_status": "never",
            "last_sync_error": None,
            "last_sync_finished_at": None,
            "last_sync_stats": None,
            "freshness_status": "unknown",
            "last_successful_trading_date": None,
            "last_metrics_trading_date": None,
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
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_ohlc_trading_date", lambda: None)
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_metrics_trading_date", lambda: None)

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
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_ohlc_trading_date", lambda: None)
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_metrics_trading_date", lambda: None)

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
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_ohlc_trading_date", lambda: None)
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_metrics_trading_date", lambda: None)

    def _raise_no_data(*_args, **_kwargs):
        raise ValueError("No stock rows found for training window.")

    monkeypatch.setattr(main, "_train_model", _raise_no_data)

    main._run_startup_training()

    health = main.health()
    assert health["status"] == "up"
    assert health["model_loaded"] is False
    assert health["training_status"] == "waiting_for_data"
    assert "No stock rows" in health["training_error"]


def test_startup_training_waits_for_fresh_analytics_before_retraining(monkeypatch) -> None:
    _reset_state()

    monkeypatch.setattr(main, "settings", SimpleNamespace(
        train_on_startup=True,
        retrain_interval_hours=168,
        default_days_back=365,
        default_horizon_days=5,
        max_training_stocks=100,
        freshness_check_enabled=True,
    ))
    monkeypatch.setattr(main, "_target_trading_date", lambda _now=None: date(2026, 9, 17))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_ohlc_trading_date", lambda: date(2026, 9, 16))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_metrics_trading_date", lambda: date(2026, 9, 16))
    monkeypatch.setattr(main, "_train_model", lambda *_args, **_kwargs: (_ for _ in ()).throw(AssertionError("training should be skipped")))

    main._run_startup_training()

    assert main.state["training_status"] == "waiting_for_freshness"
    assert "stale" in str(main.state["training_error"])


def test_scheduled_training_skips_when_analytics_data_is_stale(monkeypatch) -> None:
    _reset_state()

    main.state.update(
        {
            "estimator": object(),
            "model_name": "logistic_regression",
            "model_version": "v20260915000000",
            "horizon_days": 5,
            "training_status": "trained",
        }
    )

    monkeypatch.setattr(main, "settings", SimpleNamespace(
        train_on_startup=True,
        retrain_interval_hours=168,
        default_days_back=365,
        default_horizon_days=5,
        max_training_stocks=100,
        freshness_check_enabled=True,
    ))
    monkeypatch.setattr(main, "_target_trading_date", lambda _now=None: date(2026, 9, 17))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_ohlc_trading_date", lambda: date(2026, 9, 16))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_metrics_trading_date", lambda: date(2026, 9, 16))
    monkeypatch.setattr(main, "_train_model", lambda *_args, **_kwargs: (_ for _ in ()).throw(AssertionError("training should be skipped")))

    class StopAfterOneWait:
        def __init__(self) -> None:
            self.calls = 0

        def wait(self, _seconds: float) -> bool:
            self.calls += 1
            return self.calls > 1

    monkeypatch.setattr(main, "stop_event", StopAfterOneWait())

    main._run_scheduled_training()

    assert main.state["training_status"] == "trained"


def test_training_requires_metrics_to_catch_up_with_latest_ohlc(monkeypatch) -> None:
    _reset_state()

    monkeypatch.setattr(main, "_target_trading_date", lambda _now=None: date(2026, 9, 16))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_ohlc_trading_date", lambda: date(2026, 9, 17))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_metrics_trading_date", lambda: date(2026, 9, 16))

    ready, reason = main._training_data_is_ready_for_retrain(datetime(2026, 9, 17, 12, 0, tzinfo=timezone.utc))

    assert ready is False
    assert reason is not None
    assert "required=2026-09-17" in reason


def test_health_reflects_live_db_state(monkeypatch) -> None:
    _reset_state()
    main.state.update(
        {
            "last_sync_status": "ok",
            "last_sync_finished_at": "2026-09-12T00:00:00+00:00",
            "freshness_status": "stale",
            "expected_trading_date": "2026-09-11",
            "last_successful_trading_date": "2026-09-11",
            "last_metrics_trading_date": "2026-09-11",
        }
    )

    monkeypatch.setattr(main, "_target_trading_date", lambda _now=None: date(2026, 9, 14))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_ohlc_trading_date", lambda: date(2026, 9, 14))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_metrics_trading_date", lambda: date(2026, 9, 14))

    health = main.health()

    assert health["freshness_status"] == "fresh"
    assert health["last_successful_trading_date"] == "2026-09-14"
    assert health["last_metrics_trading_date"] == "2026-09-14"
    assert health["expected_trading_date"] == "2026-09-14"


def test_health_is_stale_when_metrics_lag_latest_ohlc_even_if_expected_is_met(monkeypatch) -> None:
    _reset_state()

    monkeypatch.setattr(main, "_target_trading_date", lambda _now=None: date(2026, 9, 16))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_ohlc_trading_date", lambda: date(2026, 9, 17))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_metrics_trading_date", lambda: date(2026, 9, 16))

    health = main.health()

    assert health["last_successful_trading_date"] == "2026-09-17"
    assert health["last_metrics_trading_date"] == "2026-09-16"
    assert health["expected_trading_date"] == "2026-09-16"
    assert health["freshness_status"] == "stale"


def test_freshness_check_schedules_retry_when_metrics_refresh_does_not_catch_up(monkeypatch) -> None:
    _reset_state()

    monkeypatch.setattr(main, "_target_trading_date", lambda _now=None: date(2026, 9, 16))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_ohlc_trading_date", lambda: date(2026, 9, 17))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_metrics_trading_date", lambda: date(2026, 9, 16))
    monkeypatch.setattr(main, "_run_analytics_sync", lambda *args, **kwargs: {"ok": True})
    monkeypatch.setattr(
        main,
        "_schedule_next_retry",
        lambda _reference=None: datetime(2026, 9, 17, 12, 30, tzinfo=timezone.utc),
    )

    main._check_freshness_and_sync(trigger="test")

    assert main.state["freshness_status"] == "stale"
    assert main.state["next_retry_at"] == "2026-09-17T12:30:00+00:00"


def test_admin_sync_nightly_triggers_full_sync(monkeypatch) -> None:
    _reset_state()

    captured: dict[str, object] = {}

    def fake_sync(trigger: str, force_metrics_refresh: bool = False):
        captured["trigger"] = trigger
        captured["force_metrics_refresh"] = force_metrics_refresh
        return {"trigger": trigger, "finished_at": "2026-09-14T00:00:00+00:00"}

    monkeypatch.setattr(main, "_run_analytics_sync", fake_sync)

    response = main.admin_sync_nightly()

    assert response["trigger"] == "manual_admin"
    assert captured["trigger"] == "manual_admin"
    assert captured["force_metrics_refresh"] is True


def test_admin_sync_status_matches_health_snapshot(monkeypatch) -> None:
    _reset_state()

    monkeypatch.setattr(main, "_target_trading_date", lambda _now=None: date(2026, 9, 14))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_ohlc_trading_date", lambda: date(2026, 9, 14))
    monkeypatch.setattr(main.analytics_sync_service, "get_latest_metrics_trading_date", lambda: date(2026, 9, 14))

    response = main.admin_sync_status()

    assert response["status"] == "up"
    assert response["freshness_status"] == "fresh"


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
        lambda stock_id: pd.DataFrame([{"stock_id": stock_id, "symbol": "AAPL", "trading_date": datetime(2026, 8, 6, tzinfo=timezone.utc)}]),
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


def test_prediction_generated_at_for_trading_day_uses_midnight_utc_for_date() -> None:
    generated_at = main._prediction_generated_at_for_trading_day(date(2026, 9, 16))

    assert generated_at == datetime(2026, 9, 16, 0, 0, tzinfo=timezone.utc)


def test_generate_prediction_snapshot_uses_history_trading_day_timestamp(monkeypatch) -> None:
    _reset_state()
    main.state.update(
        {
            "estimator": object(),
            "model_name": "logistic_regression",
            "model_version": "v20260916000000",
            "horizon_days": 5,
        }
    )

    monkeypatch.setattr(main, "build_prediction_row", lambda _history: pd.DataFrame([{"x": 1}]))
    monkeypatch.setattr(
        main,
        "predict_action",
        lambda **_kwargs: {
            "action": "BUY",
            "confidence": 0.63,
            "probability_buy": 0.6322,
            "probability_sell": 0.3678,
            "confidence_edge": 0.2644,
            "probability_gap": 0.2644,
            "conviction_label": "medium",
            "reasoning": ["momentum"],
        },
    )

    stored_rows: list[dict[str, object]] = []

    def _store(rows):
        stored_rows.extend(rows)
        return len(rows)

    monkeypatch.setattr(main.repository, "store_prediction_snapshots", _store)
    monkeypatch.setattr(
        main.repository,
        "fetch_prediction_snapshot",
        lambda stock_id, model_version: {
            "stock_id": stock_id,
            "prediction_generated_at": stored_rows[0]["prediction_generated_at"],
            "prediction_model_version": model_version,
        },
    )

    history = pd.DataFrame([{"stock_id": 1, "symbol": "AAPL", "trading_date": date(2026, 9, 16)}])
    snapshot = main._generate_prediction_snapshot(stock_id=1, history=history)

    assert stored_rows
    assert stored_rows[0]["prediction_generated_at"] == datetime(2026, 9, 16, 0, 0, tzinfo=timezone.utc)
    assert snapshot is not None
    assert snapshot["prediction_generated_at"] == datetime(2026, 9, 16, 0, 0, tzinfo=timezone.utc)


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
        lambda stock_id: pd.DataFrame([{"stock_id": stock_id, "symbol": "AAPL", "trading_date": datetime(2026, 8, 6, tzinfo=timezone.utc)}]),
    )
    monkeypatch.setattr(main.repository, "fetch_prediction_snapshot", lambda stock_id, model_version: None)
    monkeypatch.setattr(main, "predict_action", lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("force regeneration failure")))

    try:
        main.get_prediction(stock_id=1)
        assert False, "Expected HTTPException"
    except HTTPException as error:
        assert error.status_code == 503
        assert "Prediction snapshot unavailable" in str(error.detail)


