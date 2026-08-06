from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any

import pandas as pd
from sqlalchemy import create_engine, text


class StockDataRepository:
    def __init__(self, database_url: str) -> None:
        self._engine = create_engine(database_url, future=True)

    def initialize_tables(self) -> None:
        with self._engine.begin() as connection:
            # ── stocks replica (synced nightly from stock-service) ──────────────
            connection.execute(text("""
                CREATE TABLE IF NOT EXISTS stocks (
                    stock_id    BIGSERIAL PRIMARY KEY,
                    ticker      VARCHAR(20)  NOT NULL UNIQUE,
                    name        VARCHAR(255),
                    market      VARCHAR(50),
                    market_cap  NUMERIC(20, 2),
                    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                )
            """))

            # ── stock_daily_ohlc (migrated from stock-service-db) ───────────────
            connection.execute(text("""
                CREATE TABLE IF NOT EXISTS stock_daily_ohlc (
                    id                   BIGSERIAL PRIMARY KEY,
                    stock_id             BIGINT        NOT NULL REFERENCES stocks(stock_id),
                    trading_date         DATE          NOT NULL,
                    open_price           NUMERIC(12,2) NOT NULL,
                    high_price           NUMERIC(12,2) NOT NULL,
                    low_price            NUMERIC(12,2) NOT NULL,
                    close_price          NUMERIC(12,2) NOT NULL,
                    volume               BIGINT        NOT NULL DEFAULT 0,
                    sma_20               NUMERIC(12,2),
                    sma_50               NUMERIC(12,2),
                    sma_200              NUMERIC(12,2),
                    volatility_20d       NUMERIC(12,2),
                    volatility_60d       NUMERIC(12,2),
                    volatility_90d       NUMERIC(12,2),
                    return_1d            NUMERIC(12,2),
                    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                    UNIQUE (stock_id, trading_date)
                )
            """))
            # Keep table shape aligned with current API/ML usage.
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS target_week_direction"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS sentiment_score"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS daily_news"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS vwap"))
            # These per-row derived columns are not plotted historically; only latest snapshot is kept in stock_metrics.
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS return_5d"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS return_20d"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS return_60d"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS return_90d"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS return_120d"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS forward_return_5d"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS rsi_14"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS macd"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS macd_signal"))
            # volatility_5d and volatility_120d are shown as today's stats from stock_metrics, not plotted in history charts.
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS volatility_5d"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc DROP COLUMN IF EXISTS volatility_120d"))
            # Ensure chart indicator columns exist on pre-existing tables (CREATE TABLE IF NOT EXISTS won't add new columns).
            connection.execute(text("ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS sma_20 NUMERIC(12,2)"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS sma_50 NUMERIC(12,2)"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS sma_200 NUMERIC(12,2)"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS volatility_20d NUMERIC(12,2)"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS volatility_60d NUMERIC(12,2)"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS volatility_90d NUMERIC(12,2)"))
            connection.execute(text("ALTER TABLE stock_daily_ohlc ADD COLUMN IF NOT EXISTS return_1d NUMERIC(12,2)"))
            connection.execute(text("""
                CREATE INDEX IF NOT EXISTS idx_ohlc_stock_date
                    ON stock_daily_ohlc (stock_id, trading_date)
            """))

            # ── stock_metrics (migrated from stock-service-db) ──────────────────
            connection.execute(text("""
                CREATE TABLE IF NOT EXISTS stock_metrics (
                    stock_id                  BIGINT PRIMARY KEY REFERENCES stocks(stock_id),
                    week_return               NUMERIC(12,2),
                    month_return              NUMERIC(12,2),
                    three_month_return        NUMERIC(12,2),
                    six_month_return          NUMERIC(12,2),
                    year_return               NUMERIC(12,2),
                    three_year_return         NUMERIC(12,2),
                    volatility_5d             NUMERIC(12,2),
                    volatility_20d            NUMERIC(12,2),
                    volatility_60d            NUMERIC(12,2),
                    volatility_90d            NUMERIC(12,2),
                    volatility_120d           NUMERIC(12,2),
                    high_52w                  NUMERIC(12,2),
                    low_52w                   NUMERIC(12,2),
                    distance_from_high_percent NUMERIC(12,2),
                    distance_from_low_percent  NUMERIC(12,2),
                    avg_volume_30d            NUMERIC(18,2),
                    latest_trading_day_volume BIGINT,
                    latest_trading_date       DATE,
                    relative_volume           NUMERIC(12,2),
                    rsi_14                    NUMERIC(8,4),
                    macd                      NUMERIC(12,4),
                    macd_signal               NUMERIC(12,4),
                    positive_days_1y          INT,
                    negative_days_1y          INT,
                    flat_days_1y              INT,
                    monthly_returns_heatmap   TEXT,
                    max_drawdown              NUMERIC(12,2),
                    drawdown_peak_date        DATE,
                    drawdown_trough_date      DATE,
                    sharpe_ratio              NUMERIC(12,2),
                    sortino_ratio             NUMERIC(12,2),
                    golden_cross              BOOLEAN,
                    death_cross               BOOLEAN,
                    latest_news               TEXT,
                    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
            """))
            connection.execute(text("ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS volatility_5d NUMERIC(12,2)"))
            connection.execute(text("ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS volatility_20d NUMERIC(12,2)"))
            connection.execute(text("ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS volatility_60d NUMERIC(12,2)"))
            connection.execute(text("ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS volatility_90d NUMERIC(12,2)"))
            connection.execute(text("ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS volatility_120d NUMERIC(12,2)"))
            connection.execute(text("ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS latest_news TEXT"))
            connection.execute(text("ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS rsi_14 NUMERIC(8,4)"))
            connection.execute(text("ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS macd NUMERIC(12,4)"))
            connection.execute(text("ALTER TABLE stock_metrics ADD COLUMN IF NOT EXISTS macd_signal NUMERIC(12,4)"))
            # Drop sma columns from stock_metrics — trendMetrics.sma20/50/200 is read from OHLC history, not from metrics.
            connection.execute(text("ALTER TABLE stock_metrics DROP COLUMN IF EXISTS sma_20"))
            connection.execute(text("ALTER TABLE stock_metrics DROP COLUMN IF EXISTS sma_50"))
            connection.execute(text("ALTER TABLE stock_metrics DROP COLUMN IF EXISTS sma_200"))

            # ── ml_weekly_features (new) ─────────────────────────────────────────
            connection.execute(text("""
                CREATE TABLE IF NOT EXISTS ml_weekly_features (
                    id              BIGSERIAL PRIMARY KEY,
                    stock_id        BIGINT        NOT NULL REFERENCES stocks(stock_id),
                    date            DATE          NOT NULL,
                    year            INT           NOT NULL,
                    month           INT           NOT NULL,
                    week_number     INT           NOT NULL,
                    avg_return      NUMERIC(12,6) NOT NULL,
                    volatility      NUMERIC(12,6) NOT NULL,
                    next_week_label SMALLINT,
                    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                    UNIQUE (stock_id, year, week_number)
                )
            """))

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

            connection.execute(text("DROP TABLE IF EXISTS ml_predictions"))

            # Keep serial sequences aligned after manual imports/restores.
            self._repair_serial_sequence(connection, "stock_daily_ohlc", "id")
            self._repair_serial_sequence(connection, "ml_weekly_features", "id")
            self._repair_serial_sequence(connection, "ml_model_candidates", "candidate_id")

    @staticmethod
    def _repair_serial_sequence(connection, table_name: str, id_column: str) -> None:
        sequence_name = connection.execute(
            text("SELECT pg_get_serial_sequence(:table_name, :id_column)"),
            {"table_name": table_name, "id_column": id_column},
        ).scalar()
        if not sequence_name:
            return

        connection.execute(
            text(
                """
                SELECT setval(
                    :sequence_name,
                    COALESCE((SELECT MAX(id_value) FROM (SELECT {id_column} AS id_value FROM {table_name}) max_rows), 1),
                    true
                )
                """.replace("{id_column}", id_column).replace("{table_name}", table_name)
            ),
            {"sequence_name": sequence_name},
        )

    def fetch_training_data(self, days_back: int, max_training_stocks: int) -> pd.DataFrame:
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
                w.stock_id,
                rs.ticker AS symbol,
                rs.market,
                w.date AS trading_date,
                w.avg_return,
                w.volatility,
                w.next_week_label
            FROM ml_weekly_features w
            JOIN ranked_stocks rs ON rs.stock_id = w.stock_id
            WHERE w.date >= CURRENT_DATE - make_interval(days => :days_back)
              AND rs.stock_rank <= :max_training_stocks
            ORDER BY rs.market_cap DESC, w.stock_id, w.date
            """
        )
        return pd.read_sql_query(
            query,
            self._engine,
            params={
                "days_back": days_back,
                "max_training_stocks": max_training_stocks,
            },
        )

    def fetch_latest_stock_row(self, stock_id: int) -> pd.DataFrame:
        """Fetch only the latest weekly feature row for a stock.

        Prediction uses avg_return + volatility from the latest completed week.
        """
        query = text(
            """
            SELECT
                w.stock_id,
                s.ticker AS symbol,
                COALESCE(s.market, 'UNKNOWN') AS market,
                w.date AS trading_date,
                w.avg_return,
                w.volatility,
                w.next_week_label
            FROM ml_weekly_features w
            JOIN stocks s ON s.stock_id = w.stock_id
            WHERE w.stock_id = :stock_id
            ORDER BY w.date DESC
            LIMIT 1
            """
        )
        return pd.read_sql_query(
            query,
            self._engine,
            params={"stock_id": stock_id},
        )

    def fetch_stock_insights_payload(self, stock_id: int, history_days: int = 365 * 3 + 1) -> dict[str, Any] | None:
        stock_query = text(
            """
            SELECT stock_id, ticker, name, market
            FROM stocks
            WHERE stock_id = :stock_id
            """
        )
        metrics_query = text(
            """
            SELECT *
            FROM stock_metrics
            WHERE stock_id = :stock_id
            """
        )
        history_query = text(
            """
            SELECT
                trading_date,
                open_price,
                high_price,
                low_price,
                close_price,
                volume,
                sma_20,
                sma_50,
                sma_200,
                volatility_20d,
                volatility_60d,
                volatility_90d,
                return_1d
            FROM stock_daily_ohlc
            WHERE stock_id = :stock_id
              AND trading_date >= CURRENT_DATE - make_interval(days => :history_days)
            ORDER BY trading_date ASC
            """
        )

        with self._engine.begin() as connection:
            stock_row = connection.execute(stock_query, {"stock_id": stock_id}).mappings().first()
            if stock_row is None:
                return None
            metrics_row = connection.execute(metrics_query, {"stock_id": stock_id}).mappings().first()
            history = connection.execute(
                history_query,
                {"stock_id": stock_id, "history_days": max(30, int(history_days))},
            ).mappings().all()

        history_items: list[dict[str, Any]] = []
        for row in history:
            history_items.append(
                {
                    "tradingDate": row["trading_date"].isoformat() if row.get("trading_date") else None,
                    "open": _float_or_none(row.get("open_price")),
                    "high": _float_or_none(row.get("high_price")),
                    "low": _float_or_none(row.get("low_price")),
                    "close": _float_or_none(row.get("close_price")),
                    "volume": _int_or_none(row.get("volume")),
                    "sma20": _float_or_none(row.get("sma_20")),
                    "sma50": _float_or_none(row.get("sma_50")),
                    "sma200": _float_or_none(row.get("sma_200")),
                    "volatility20Day": _float_or_none(row.get("volatility_20d")),
                    "volatility60Day": _float_or_none(row.get("volatility_60d")),
                    "volatility90Day": _float_or_none(row.get("volatility_90d")),
                    "return1d": _float_or_none(row.get("return_1d")),
                }
            )

        latest = history_items[-1] if history_items else None
        previous = history_items[-2] if len(history_items) > 1 else None
        current_price = latest.get("close") if latest else None
        previous_close = previous.get("close") if previous else None
        daily_change = (current_price - previous_close) if current_price is not None and previous_close is not None else None
        daily_change_percent = ((daily_change / previous_close) * 100.0) if daily_change is not None and previous_close not in (None, 0) else None

        monthly_heatmap = []
        latest_news_blob = None
        if metrics_row is not None:
            latest_news_blob = metrics_row.get("latest_news")
            raw_heatmap = metrics_row.get("monthly_returns_heatmap")
            if isinstance(raw_heatmap, str) and raw_heatmap.strip():
                try:
                    decoded = json.loads(raw_heatmap)
                    if isinstance(decoded, list):
                        monthly_heatmap = decoded
                except Exception:
                    monthly_heatmap = []

        latest_trading_date = metrics_row.get("latest_trading_date") if metrics_row else (latest.get("trading_date") if latest else None)
        latest_news_items = []
        if latest_news_blob:
            latest_news_items.append(
                {
                    "tradingDate": latest_trading_date.isoformat() if latest_trading_date else None,
                    "news": str(latest_news_blob),
                }
            )

        return {
            "id": str(stock_row["stock_id"]),
            "symbol": stock_row.get("ticker"),
            "name": stock_row.get("name") or stock_row.get("ticker"),
            "exchange": stock_row.get("market"),
            "market": stock_row.get("market"),
            "lastUpdated": _iso_or_none(metrics_row.get("updated_at") if metrics_row else None),
            "currentPerformance": {
                "currentPrice": current_price,
                "previousClose": previous_close,
                "dailyChange": daily_change,
                "dailyChangePercent": daily_change_percent,
            },
            "metrics52Week": {
                "high52Week": _float_or_none(metrics_row.get("high_52w") if metrics_row else None),
                "low52Week": _float_or_none(metrics_row.get("low_52w") if metrics_row else None),
                "distanceFromHighPercent": _float_or_none(metrics_row.get("distance_from_high_percent") if metrics_row else None),
                "distanceFromLowPercent": _float_or_none(metrics_row.get("distance_from_low_percent") if metrics_row else None),
            },
            "returns": {
                "oneWeekReturn": _float_or_none(metrics_row.get("week_return") if metrics_row else None),
                "oneMonthReturn": _float_or_none(metrics_row.get("month_return") if metrics_row else None),
                "threeMonthReturn": _float_or_none(metrics_row.get("three_month_return") if metrics_row else None),
                "sixMonthReturn": _float_or_none(metrics_row.get("six_month_return") if metrics_row else None),
                "oneYearReturn": _float_or_none(metrics_row.get("year_return") if metrics_row else None),
                "threeYearReturn": _float_or_none(metrics_row.get("three_year_return") if metrics_row else None),
            },
            "volumeMetrics": {
                "latestTradingDayVolume": _int_or_none(metrics_row.get("latest_trading_day_volume") if metrics_row else None),
                "latestTradingDate": latest_trading_date.isoformat() if latest_trading_date else None,
                "average30DayVolume": _float_or_none(metrics_row.get("avg_volume_30d") if metrics_row else None),
                "relativeVolume": _float_or_none(metrics_row.get("relative_volume") if metrics_row else None),
            },
            "volatilityMetrics": {
                "volatility5Day": _float_or_none(metrics_row.get("volatility_5d") if metrics_row else None),
                "volatility20Day": _float_or_none(metrics_row.get("volatility_20d") if metrics_row else None),
                "volatility60Day": _float_or_none(metrics_row.get("volatility_60d") if metrics_row else None),
                "volatility90Day": _float_or_none(metrics_row.get("volatility_90d") if metrics_row else None),
                "volatility120Day": _float_or_none(metrics_row.get("volatility_120d") if metrics_row else None),
            },
            "trendMetrics": {
                "sma20": latest.get("sma20") if latest else None,
                "sma50": latest.get("sma50") if latest else None,
                "sma200": latest.get("sma200") if latest else None,
                "goldenCross": _bool_or_none(metrics_row.get("golden_cross") if metrics_row else None),
                "deathCross": _bool_or_none(metrics_row.get("death_cross") if metrics_row else None),
            },
            "momentumMetrics": {
                "rsi14": _float_or_none(metrics_row.get("rsi_14") if metrics_row else None),
                "macd": _float_or_none(metrics_row.get("macd") if metrics_row else None),
                "macdSignal": _float_or_none(metrics_row.get("macd_signal") if metrics_row else None),
            },
            "riskMetrics": {
                "sharpeRatio": _float_or_none(metrics_row.get("sharpe_ratio") if metrics_row else None),
                "sortinoRatio": _float_or_none(metrics_row.get("sortino_ratio") if metrics_row else None),
                "maxDrawdown": _float_or_none(metrics_row.get("max_drawdown") if metrics_row else None),
            },
            "performanceDistribution": {
                "positiveDays": _int_or_none(metrics_row.get("positive_days_1y") if metrics_row else None) or 0,
                "negativeDays": _int_or_none(metrics_row.get("negative_days_1y") if metrics_row else None) or 0,
                "flatDays": _int_or_none(metrics_row.get("flat_days_1y") if metrics_row else None) or 0,
            },
            "drawdownAnalysis": {
                "maxDrawdown": _float_or_none(metrics_row.get("max_drawdown") if metrics_row else None),
                "peakDate": _iso_or_none(metrics_row.get("drawdown_peak_date") if metrics_row else None),
                "troughDate": _iso_or_none(metrics_row.get("drawdown_trough_date") if metrics_row else None),
            },
            "latestNews": latest_news_items,
            "monthlyReturnsHeatmap": monthly_heatmap,
            "history": history_items,
        }

    def fetch_analytics_news(self, limit: int = 10) -> list[dict[str, Any]]:
        query = text(
            """
            SELECT
                s.stock_id,
                s.ticker,
                m.latest_trading_date,
                m.latest_news
            FROM stock_metrics m
            JOIN stocks s ON s.stock_id = m.stock_id
            WHERE m.latest_news IS NOT NULL
              AND length(m.latest_news) > 2
            ORDER BY COALESCE(s.market_cap, 0) DESC, s.stock_id ASC
            LIMIT :limit
            """
        )
        frame = pd.read_sql_query(query, self._engine, params={"limit": max(1, int(limit))})
        rows: list[dict[str, Any]] = []
        for _, row in frame.iterrows():
            trading_date = row.get("latest_trading_date")
            rows.append(
                {
                    "stockId": int(row["stock_id"]),
                    "symbol": str(row["ticker"]),
                    "tradingDate": trading_date.date().isoformat() if hasattr(trading_date, "date") else (str(trading_date) if trading_date is not None else None),
                    "news": str(row.get("latest_news")) if pd.notna(row.get("latest_news")) else None,
                }
            )
        return rows

    def save_model_registry(self, payload: dict[str, Any]) -> None:
        with self._engine.begin() as connection:
            connection.execute(
                text(
                    """
                    SELECT setval(
                        pg_get_serial_sequence('ml_model_candidates', 'candidate_id'),
                        COALESCE((SELECT MAX(candidate_id) FROM ml_model_candidates), 1),
                        true
                    )
                    """
                )
            )
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


def _float_or_none(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _int_or_none(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _bool_or_none(value: Any) -> bool | None:
    if value is None:
        return None
    return bool(value)


def _iso_or_none(value: Any) -> str | None:
    if value is None:
        return None
    if hasattr(value, "isoformat"):
        return value.isoformat()
    return str(value)


