from __future__ import annotations

import numpy as np
import pandas as pd

from app.ml_pipeline import _derive_action, _split_train_test_by_date, build_prediction_row, train_and_select_model


def synthetic_frame(rows_per_stock: int = 320, stocks: int = 4) -> pd.DataFrame:
    rng = np.random.default_rng(42)
    records: list[dict[str, object]] = []
    for stock_id in range(1, stocks + 1):
        close = 100.0 + stock_id
        for day in range(rows_per_stock):
            drift = 0.03 if stock_id % 2 == 0 else -0.01
            shock = rng.normal(0.0, 1.2)
            close = max(5.0, close + drift + shock)
            open_price = close + rng.normal(0.0, 0.8)
            high_price = max(open_price, close) + abs(rng.normal(0.4, 0.3))
            low_price = min(open_price, close) - abs(rng.normal(0.4, 0.3))
            records.append(
                {
                    "stock_id": stock_id,
                    "symbol": f"STK{stock_id}",
                    "market": "stocks",
                    "trading_date": pd.Timestamp("2021-01-01") + pd.Timedelta(days=day),
                    "open_price": open_price,
                    "high_price": high_price,
                    "low_price": low_price,
                    "close_price": close,
                    "volatility_5d": abs(rng.normal(1.6, 0.5)),
                    "volatility_20d": abs(rng.normal(1.9, 0.5)),
                    "volatility_60d": abs(rng.normal(2.1, 0.6)),
                    "volatility_90d": abs(rng.normal(2.3, 0.7)),
                    "volatility_120d": abs(rng.normal(2.5, 0.8)),
                    "return_1d": rng.normal(0.1, 1.5),
                    "return_5d": rng.normal(0.5, 2.5),
                    "return_20d": rng.normal(1.2, 4.5),
                    "return_60d": rng.normal(3.0, 8.0),
                    "return_90d": rng.normal(4.5, 10.0),
                    "return_120d": rng.normal(6.0, 12.0),
                }
            )
    return pd.DataFrame(records)


def add_week_targets(frame: pd.DataFrame) -> pd.DataFrame:
    enriched = frame.sort_values(["stock_id", "trading_date"]).reset_index(drop=True).copy()
    grouped = enriched.groupby("stock_id", group_keys=False)
    enriched["forward_return_5d"] = (
        grouped["close_price"].shift(-5).subtract(enriched["close_price"])
        .divide(enriched["close_price"])
        .mul(100)
    )
    enriched["target_week_direction"] = np.where(
        enriched["forward_return_5d"] > 0,
        "POSITIVE",
        np.where(enriched["forward_return_5d"] < 0, "NEGATIVE", np.where(enriched["forward_return_5d"].isna(), None, "FLAT")),
    )
    return enriched


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
    frame.loc[0, "target_week_direction"] = "FLAT"
    bundle_input = frame.copy()

    from app.ml_pipeline import _engineer_features

    engineered = _engineer_features(bundle_input, horizon_days=5, positive_return_threshold=0.015, neutral_return_band=0.015)
    assert pd.isna(engineered.iloc[0]["target"])
    assert engineered.tail(5)["target"].isna().all()


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


