from __future__ import annotations

import numpy as np
import pandas as pd

from app.ml_pipeline import build_prediction_row, train_and_select_model


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
                    "volume": float(1_000_000 + rng.normal(0, 70_000)),
                    "sma_20": close + rng.normal(0, 0.5),
                    "sma_50": close + rng.normal(0, 0.9),
                    "sma_200": close + rng.normal(0, 1.2),
                    "volatility_30d": abs(rng.normal(2.0, 0.6)),
                    "volatility_90d": abs(rng.normal(2.4, 0.7)),
                    "daily_return_percent": rng.normal(0.1, 1.5),
                }
            )
    return pd.DataFrame(records)


def test_training_selects_model() -> None:
    frame = synthetic_frame()
    bundle = train_and_select_model(frame, horizon_days=5, positive_return_threshold=0.0)

    assert bundle.estimator is not None
    assert bundle.selected_model in {
        "logistic_regression",
        "random_forest",
        "gradient_boosting",
        "extra_trees",
        "xgboost",
    }
    assert bundle.trained_rows > 600
    assert len(bundle.metrics) == 5


def test_prediction_row_contains_latest_stock_record() -> None:
    frame = synthetic_frame(rows_per_stock=80, stocks=1)
    row = build_prediction_row(frame)

    assert len(row) == 1
    assert int(row.iloc[0]["stock_id"]) == 1
    assert str(row.iloc[0]["symbol"]) == "STK1"

