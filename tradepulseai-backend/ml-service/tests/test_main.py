from __future__ import annotations

from types import SimpleNamespace

import app.main as main


def _reset_state() -> None:
    main.state.update(
        {
            "estimator": None,
            "model_name": None,
            "model_version": None,
            "horizon_days": None,
            "positive_return_threshold": None,
            "decision_threshold": 0.55,
            "training_status": "pending",
            "training_error": None,
            "last_trained_at": None,
        }
    )


def test_startup_survives_missing_training_data(monkeypatch) -> None:
    _reset_state()

    monkeypatch.setattr(main, "settings", SimpleNamespace(
        train_on_startup=True,
        retrain_interval_hours=0,
        default_days_back=1095,
        default_horizon_days=5,
        default_positive_return_threshold=0.0,
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
        default_days_back=180,
        default_horizon_days=5,
        default_positive_return_threshold=0.0,
        max_training_stocks=100,
        max_training_rows=30000,
    ))
    monkeypatch.setattr(main.repository, "initialize_tables", lambda: None)
    monkeypatch.setattr(main, "_load_model_from_disk", lambda: False)
    monkeypatch.setattr(main, "_persist_trained_model", lambda trained: main.state.update(
        {
            "estimator": trained.estimator,
            "model_name": trained.selected_model,
            "model_version": trained.model_version,
            "horizon_days": trained.horizon_days,
            "positive_return_threshold": trained.positive_return_threshold,
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
        positive_return_threshold = 0.0
        decision_threshold = 0.55
        trained_rows = 1234
        metrics = [{
            "cv_f1": 0.6,
            "test_f1": 0.7,
            "test_balanced_accuracy": 0.65,
            "test_precision": 0.68,
            "test_recall": 0.72,
            "test_action_rate": 0.81,
            "test_hold_rate": 0.19,
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
        default_days_back=180,
        default_horizon_days=5,
        default_positive_return_threshold=0.0,
        max_training_stocks=100,
        max_training_rows=30000,
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

