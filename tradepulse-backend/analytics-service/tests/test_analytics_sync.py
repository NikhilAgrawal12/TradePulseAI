from __future__ import annotations

from datetime import date

import pandas as pd

from app.analytics_sync import _build_weekly_feature_rows


REQUIRED_FEATURES = {
    "return_5d": 1.0,
    "return_10d": 2.0,
    "return_20d": 3.0,
    "volatility_5d": 0.5,
    "volatility_10d": 0.7,
    "volatility_20d": 1.1,
    "sma20_distance": 0.2,
    "sma50_distance": 0.4,
    "rsi": 55.0,
    "macd": 0.9,
    "volume_change": 4.5,
    "label": 1,
}


def weekly_row(stock_id: int, trading_date: str, **overrides: float | int | str | None) -> dict[str, object]:
    row: dict[str, object] = {
        "stock_id": stock_id,
        "trading_date": trading_date,
        **REQUIRED_FEATURES,
    }
    row.update(overrides)
    return row


def test_build_weekly_feature_rows_keeps_latest_row_per_week_for_allowed_stocks() -> None:
    frame = pd.DataFrame(
        [
            weekly_row(1, "2026-09-07", return_5d=1.0),
            weekly_row(1, "2026-09-11", return_5d=9.0),
            weekly_row(1, "2026-09-14", return_5d=11.0),
            weekly_row(1, "2026-09-16", return_5d=13.0),
            weekly_row(2, "2026-09-08", return_5d=2.0, label=0),
            weekly_row(2, "2026-09-10", return_5d=8.0, label=0),
            weekly_row(2, "2026-09-15", return_5d=12.0, label=0),
            weekly_row(99, "2026-09-11", return_5d=99.0),
        ]
    )

    rows = _build_weekly_feature_rows(
        frame,
        allowed_stock_ids={1, 2},
        end_date=date(2026, 9, 16),
        history_days=730,
    )

    assert [(row["stock_id"], row["date"], row["return_5d"]) for row in rows] == [
        (1, date(2026, 9, 11), 9.0),
        (1, date(2026, 9, 16), 13.0),
        (2, date(2026, 9, 10), 8.0),
        (2, date(2026, 9, 15), 12.0),
    ]


def test_build_weekly_feature_rows_filters_old_and_incomplete_rows() -> None:
    frame = pd.DataFrame(
        [
            weekly_row(1, "2026-08-01", return_5d=1.0),
            weekly_row(1, "2026-09-08", return_5d=2.0),
            weekly_row(1, "2026-09-12", return_5d=None),
            weekly_row(1, "2026-09-15", return_5d=3.0),
        ]
    )

    rows = _build_weekly_feature_rows(
        frame,
        allowed_stock_ids={1},
        end_date=date(2026, 9, 16),
        history_days=30,
    )

    assert [(row["date"], row["return_5d"]) for row in rows] == [
        (date(2026, 9, 8), 2.0),
        (date(2026, 9, 15), 3.0),
    ]

