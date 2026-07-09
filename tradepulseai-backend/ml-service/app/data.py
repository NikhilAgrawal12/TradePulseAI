from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

import pandas as pd
from sqlalchemy import create_engine, text


class StockDataRepository:
    def __init__(self, database_url: str) -> None:
        self._engine = create_engine(database_url, future=True)

    def initialize_tables(self) -> None:
        with self._engine.begin() as connection:
            connection.execute(
                text(
                    """
                    CREATE TABLE IF NOT EXISTS ml_model_registry (
                        model_version VARCHAR(64) PRIMARY KEY,
                        model_name VARCHAR(100) NOT NULL,
                        horizon_days INTEGER NOT NULL,
                        positive_return_threshold DOUBLE PRECISION NOT NULL,
                        decision_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.55,
                        trained_rows INTEGER NOT NULL,
                        cv_f1 DOUBLE PRECISION NOT NULL,
                        test_f1 DOUBLE PRECISION NOT NULL,
                        test_balanced_accuracy DOUBLE PRECISION NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """
                )
            )
            connection.execute(text("ALTER TABLE ml_model_registry ADD COLUMN IF NOT EXISTS test_precision DOUBLE PRECISION"))
            connection.execute(text("ALTER TABLE ml_model_registry ADD COLUMN IF NOT EXISTS test_recall DOUBLE PRECISION"))
            connection.execute(text("ALTER TABLE ml_model_registry ADD COLUMN IF NOT EXISTS decision_threshold DOUBLE PRECISION DEFAULT 0.55"))

            connection.execute(
                text(
                    """
                    CREATE TABLE IF NOT EXISTS ml_model_candidates (
                        candidate_id BIGSERIAL PRIMARY KEY,
                        model_version VARCHAR(64) NOT NULL,
                        model_name VARCHAR(100) NOT NULL,
                        model_rank INTEGER NOT NULL,
                        is_selected BOOLEAN NOT NULL DEFAULT FALSE,
                        cv_f1 DOUBLE PRECISION NOT NULL,
                         test_f1 DOUBLE PRECISION NOT NULL,
                         test_balanced_accuracy DOUBLE PRECISION NOT NULL,
                         test_precision DOUBLE PRECISION,
                         test_recall DOUBLE PRECISION,
                         created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                         UNIQUE (model_version, model_name)
                     )
                    """
                )
            )

            connection.execute(
                text(
                    """
                    CREATE TABLE IF NOT EXISTS ml_predictions (
                        prediction_id BIGSERIAL PRIMARY KEY,
                        model_version VARCHAR(64) NOT NULL,
                        stock_id BIGINT NOT NULL,
                        symbol VARCHAR(20) NOT NULL,
                        action VARCHAR(20) NOT NULL,
                        confidence DOUBLE PRECISION NOT NULL,
                        probability_buy DOUBLE PRECISION NOT NULL,
                        probability_sell DOUBLE PRECISION NOT NULL,
                        horizon_days INTEGER NOT NULL,
                        generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """
                )
            )

    def fetch_training_data(self, days_back: int, max_training_stocks: int, max_training_rows: int) -> pd.DataFrame:
        query = text(
            """
            WITH ranked_stocks AS (
                SELECT
                    s.stock_id,
                    s.ticker,
                    COALESCE(s.market, 'UNKNOWN') AS market,
                    COALESCE(s.market_cap, 0) AS market_cap,
                    ROW_NUMBER() OVER (ORDER BY COALESCE(s.market_cap, 0) DESC, s.stock_id ASC) AS stock_rank
                FROM stocks s
            )
            SELECT
                d.stock_id,
                rs.ticker AS symbol,
                rs.market,
                d.trading_date,
                d.open_price,
                d.high_price,
                d.low_price,
                d.close_price,
                d.volume,
                d.sma_20,
                d.sma_50,
                d.sma_200,
                d.volatility_30d,
                d.volatility_90d,
                d.daily_return_percent
            FROM stock_daily_ohlc d
            JOIN ranked_stocks rs ON rs.stock_id = d.stock_id
            WHERE d.trading_date >= CURRENT_DATE - make_interval(days => :days_back)
              AND rs.stock_rank <= :max_training_stocks
            ORDER BY rs.market_cap DESC, d.stock_id, d.trading_date
            LIMIT :max_training_rows
            """
        )
        return pd.read_sql_query(
            query,
            self._engine,
            params={
                "days_back": days_back,
                "max_training_stocks": max_training_stocks,
                "max_training_rows": max_training_rows,
            },
        )

    def fetch_stock_history(self, stock_id: int, lookback_rows: int = 260) -> pd.DataFrame:
        query = text(
            """
            SELECT * FROM (
                SELECT
                    d.stock_id,
                    s.ticker AS symbol,
                    COALESCE(s.market, 'UNKNOWN') AS market,
                    d.trading_date,
                    d.open_price,
                    d.high_price,
                    d.low_price,
                    d.close_price,
                    d.volume,
                    d.sma_20,
                    d.sma_50,
                    d.sma_200,
                    d.volatility_30d,
                    d.volatility_90d,
                    d.daily_return_percent
                FROM stock_daily_ohlc d
                JOIN stocks s ON s.stock_id = d.stock_id
                WHERE d.stock_id = :stock_id
                ORDER BY d.trading_date DESC
                LIMIT :lookback_rows
            ) src
            ORDER BY src.trading_date
            """
        )
        return pd.read_sql_query(
            query,
            self._engine,
            params={"stock_id": stock_id, "lookback_rows": lookback_rows},
        )

    def save_model_registry(self, payload: dict[str, Any]) -> None:
        with self._engine.begin() as connection:
            connection.execute(
                text(
                    """
                    INSERT INTO ml_model_registry (
                        model_version,
                        model_name,
                        horizon_days,
                        positive_return_threshold,
                        decision_threshold,
                         trained_rows,
                         cv_f1,
                         test_f1,
                         test_balanced_accuracy,
                         test_precision,
                         test_recall,
                         created_at
                     )
                     VALUES (
                         :model_version,
                         :model_name,
                         :horizon_days,
                         :positive_return_threshold,
                         :decision_threshold,
                         :trained_rows,
                         :cv_f1,
                         :test_f1,
                         :test_balanced_accuracy,
                         :test_precision,
                         :test_recall,
                         :created_at
                     )
                     ON CONFLICT (model_version)
                     DO UPDATE SET
                         model_name = EXCLUDED.model_name,
                         horizon_days = EXCLUDED.horizon_days,
                         positive_return_threshold = EXCLUDED.positive_return_threshold,
                         decision_threshold = EXCLUDED.decision_threshold,
                         trained_rows = EXCLUDED.trained_rows,
                         cv_f1 = EXCLUDED.cv_f1,
                         test_f1 = EXCLUDED.test_f1,
                         test_balanced_accuracy = EXCLUDED.test_balanced_accuracy,
                         test_precision = EXCLUDED.test_precision,
                         test_recall = EXCLUDED.test_recall,
                         created_at = EXCLUDED.created_at
                    """
                ),
                {
                    **payload,
                    "created_at": datetime.now(timezone.utc),
                },
            )

    def save_model_candidates(self, model_version: str, metrics: list[dict[str, Any]], selected_model: str) -> None:
        if not metrics:
            return

        created_at = datetime.now(timezone.utc)
        rows = [
             {
                 "model_version": model_version,
                 "model_name": str(metric["model_name"]),
                 "model_rank": index + 1,
                 "is_selected": str(metric["model_name"]) == selected_model,
                 "cv_f1": float(metric["cv_f1"]),
                 "test_f1": float(metric["test_f1"]),
                 "test_balanced_accuracy": float(metric["test_balanced_accuracy"]),
                 "test_precision": float(metric["test_precision"]),
                 "test_recall": float(metric["test_recall"]),
                 "created_at": created_at,
             }
             for index, metric in enumerate(metrics)
         ]

        with self._engine.begin() as connection:
            connection.execute(
                text(
                    """
                     INSERT INTO ml_model_candidates (
                         model_version,
                         model_name,
                         model_rank,
                         is_selected,
                         cv_f1,
                         test_f1,
                         test_balanced_accuracy,
                         test_precision,
                         test_recall,
                         created_at
                     )
                     VALUES (
                         :model_version,
                         :model_name,
                         :model_rank,
                         :is_selected,
                         :cv_f1,
                         :test_f1,
                         :test_balanced_accuracy,
                         :test_precision,
                         :test_recall,
                         :created_at
                     )
                     ON CONFLICT (model_version, model_name)
                     DO UPDATE SET
                         model_rank = EXCLUDED.model_rank,
                         is_selected = EXCLUDED.is_selected,
                         cv_f1 = EXCLUDED.cv_f1,
                         test_f1 = EXCLUDED.test_f1,
                         test_balanced_accuracy = EXCLUDED.test_balanced_accuracy,
                         test_precision = EXCLUDED.test_precision,
                         test_recall = EXCLUDED.test_recall,
                        created_at = EXCLUDED.created_at
                    """
                ),
                rows,
            )

    def fetch_model_metrics(self, model_version: str) -> dict[str, float | None] | None:
        query = text(
            """
            SELECT cv_f1, test_f1, test_balanced_accuracy, test_precision, test_recall
            FROM ml_model_registry
            WHERE model_version = :model_version
            """
        )
        frame = pd.read_sql_query(query, self._engine, params={"model_version": model_version})
        if frame.empty:
            return None

        row = frame.iloc[0]
        return {
            "cv_f1": float(row["cv_f1"]) if pd.notna(row["cv_f1"]) else None,
            "test_f1": float(row["test_f1"]) if pd.notna(row["test_f1"]) else None,
            "test_balanced_accuracy": float(row["test_balanced_accuracy"]) if pd.notna(row["test_balanced_accuracy"]) else None,
            "test_precision": float(row["test_precision"]) if pd.notna(row["test_precision"]) else None,
            "test_recall": float(row["test_recall"]) if pd.notna(row["test_recall"]) else None,
        }

    def save_prediction(self, payload: dict[str, Any]) -> None:
        with self._engine.begin() as connection:
            connection.execute(
                text(
                    """
                    INSERT INTO ml_predictions (
                        model_version,
                        stock_id,
                        symbol,
                        action,
                        confidence,
                        probability_buy,
                        probability_sell,
                        horizon_days,
                        generated_at
                    )
                    VALUES (
                        :model_version,
                        :stock_id,
                        :symbol,
                        :action,
                        :confidence,
                        :probability_buy,
                        :probability_sell,
                        :horizon_days,
                        :generated_at
                    )
                    """
                ),
                {
                    **payload,
                    "generated_at": datetime.now(timezone.utc),
                },
            )
