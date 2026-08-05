from __future__ import annotations

import numpy as np
import pandas as pd

from app.ml_pipeline import _derive_action, _split_train_test_by_date, build_prediction_row, train_and_select_model


def synthetic_frame(rows_per_stock: int = 180, stocks: int = 4) -> pd.DataFrame:
    rng = np.random.default_rng(42)
    records: list[dict[str, object]] = []
    for stock_id in range(1, stocks + 1):
        for week in range(rows_per_stock):
            avg_return = rng.normal(0.25 if stock_id % 2 == 0 else -0.10, 1.4)
            volatility = abs(rng.normal(2.0, 0.6))
            score = avg_return - 0.15 * volatility + rng.normal(0.0, 0.6)
            records.append(
                {
                    "stock_id": stock_id,
                    "symbol": f"STK{stock_id}",
                    "market": "stocks",
                    "trading_date": pd.Timestamp("2021-01-04") + pd.Timedelta(days=7 * week),
                    "avg_return": avg_return,
                    "volatility": volatility,
                    "next_week_label": 1 if score > 0 else 0,
                }
            )
    return pd.DataFrame(records)


def add_week_targets(frame: pd.DataFrame) -> pd.DataFrame:
    return frame.sort_values(["stock_id", "trading_date"]).reset_index(drop=True).copy()


def test_training_selects_model() -> None:
    frame = add_week_targets(synthetic_frame())
    bundle = train_and_select_model(frame, horizon_days=5, positive_return_threshold=0.015, neutral_return_band=0.015)

    assert bundle.estimator is not None
    assert bundle.selected_model in {
        "logistic_regression",
        "random_forest",
        "gradient_boosting",
        "xgboost",
    }
    assert bundle.trained_rows > 600
    assert len(bundle.metrics) >= 4
    top_metric = bundle.metrics[0]


def test_temporal_split_uses_unique_trading_dates() -> None:
    frame = add_week_targets(synthetic_frame(rows_per_stock=80, stocks=3))
    bundle_input = frame.sort_values(["trading_date", "stock_id"]).reset_index(drop=True)

    train_df, test_df = _split_train_test_by_date(bundle_input)

    train_dates = set(pd.to_datetime(train_df["trading_date"]).dt.normalize())
    test_dates = set(pd.to_datetime(test_df["trading_date"]).dt.normalize())

    assert train_dates
    assert test_dates
    assert train_dates.isdisjoint(test_dates)
    assert max(train_dates) < min(test_dates)


def test_prediction_row_contains_latest_stock_record() -> None:
    frame = add_week_targets(synthetic_frame(rows_per_stock=80, stocks=1))
    row = build_prediction_row(frame)

    assert len(row) == 1
    assert int(row.iloc[0]["stock_id"]) == 1
    assert str(row.iloc[0]["symbol"]) == "STK1"


def test_engineered_targets_skip_null_and_flat_rows() -> None:
    frame = add_week_targets(synthetic_frame(rows_per_stock=40, stocks=1))
    frame.loc[0, "next_week_label"] = np.nan
    bundle_input = frame.copy()

    from app.ml_pipeline import _engineer_features

    engineered = _engineer_features(bundle_input, horizon_days=5, positive_return_threshold=0.015, neutral_return_band=0.015)
    assert pd.isna(engineered.iloc[0]["target"])
    assert engineered["target"].notna().any()


def test_derive_action_prefers_higher_sell_probability_when_both_clear_threshold() -> None:
    action, confidence = _derive_action(probability_sell=0.58, probability_buy=0.52, decision_threshold=0.50)

    assert action == "SELL"
    assert confidence == 0.58


def test_derive_action_prefers_higher_buy_probability_when_both_clear_threshold() -> None:
    action, confidence = _derive_action(probability_sell=0.51, probability_buy=0.57, decision_threshold=0.50)

    assert action == "BUY"
    assert confidence == 0.57


def test_derive_action_returns_hold_on_exact_tie_above_threshold() -> None:
    action, confidence = _derive_action(probability_sell=0.55, probability_buy=0.55, decision_threshold=0.50)

    assert action == "HOLD"
    assert confidence == 0.55


