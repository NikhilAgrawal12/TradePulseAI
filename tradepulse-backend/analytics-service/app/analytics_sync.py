from __future__ import annotations

import json
import logging
import math
import re
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import Any

import httpx
import pandas as pd
from pyspark.sql import SparkSession, functions as F
from pyspark.sql.window import Window
from sqlalchemy import text

from app.data import StockDataRepository
from app.java_runtime import ensure_java_runtime

logger = logging.getLogger(__name__)


@dataclass
class PipelineStats:
    trigger: str
    synced_stocks: int
    inserted_or_updated_ohlc_rows: int
    ohlc_dependency_updates: int
    ohlc_start_date: date | None
    ohlc_end_date: date | None
    metrics_rows: int
    weekly_feature_rows: int
    finished_at: str


class AnalyticsSyncService:
    """Runs nightly replica sync + OHLC ingestion + PySpark metrics refresh."""

    def __init__(self, repository: StockDataRepository, settings: Any) -> None:
        self._repository = repository
        self._settings = settings

    def get_latest_ohlc_trading_date(self) -> date | None:
        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            latest = connection.execute(text("SELECT MAX(trading_date) FROM stock_daily_ohlc")).scalar_one_or_none()
        return latest if isinstance(latest, date) else None

    def is_provider_ohlc_available_for_date(self, trading_date: date) -> bool:
        api_key = getattr(self._settings, "massive_api_key", "")
        if not api_key:
            logger.warning("Skipping provider availability probe because MASSIVE_API_KEY is not configured.")
            return False

        adjusted = bool(getattr(self._settings, "ohlc_adjusted", True))
        include_otc = bool(getattr(self._settings, "ohlc_include_otc", False))
        base_url = getattr(self._settings, "massive_api_base_url", "https://api.massive.com")

        params = {
            "adjusted": str(adjusted).lower(),
            "include_otc": str(include_otc).lower(),
            "apiKey": api_key,
        }
        path = f"/v2/aggs/grouped/locale/us/market/stocks/{trading_date.isoformat()}"

        try:
            with httpx.Client(base_url=base_url, timeout=20.0) as client:
                response = client.get(path, params=params)
            if response.status_code == 404:
                return False
            response.raise_for_status()
        except Exception as exc:  # pragma: no cover - external dependency
            logger.warning("Provider availability probe failed for %s: %s", trading_date, exc)
            return False

        payload = response.json()
        results = payload.get("results") if isinstance(payload, dict) else None
        return isinstance(results, list) and len(results) > 0

    def run_pipeline(self, trigger: str) -> PipelineStats:
        synced_stocks = self.sync_stocks_replica() if bool(getattr(self._settings, "stock_replica_sync_enabled", False)) else 0
        ohlc_rows, start_date, end_date = self.sync_missing_ohlc()
        ohlc_dependency_updates = self._backfill_return_1d()
        metrics_rows = 0
        weekly_rows = 0
        if (ohlc_rows + ohlc_dependency_updates) > 0:
            if self._is_ohlc_ready_for_metrics():
                metrics_rows, weekly_rows = self.refresh_metrics_with_pyspark()
            else:
                logger.warning("Skipping metrics/weekly refresh because OHLC dependency columns are incomplete.")
        else:
            logger.info("Skipping metrics and weekly feature refresh because OHLC had no fresh rows or dependency updates.")

        return PipelineStats(
            trigger=trigger,
            synced_stocks=synced_stocks,
            inserted_or_updated_ohlc_rows=ohlc_rows,
            ohlc_dependency_updates=ohlc_dependency_updates,
            ohlc_start_date=start_date,
            ohlc_end_date=end_date,
            metrics_rows=metrics_rows,
            weekly_feature_rows=weekly_rows,
            finished_at=datetime.now(timezone.utc).isoformat(),
        )

    def _backfill_return_1d(self) -> int:
        sql = text(
            """
            WITH computed AS (
                SELECT
                    t.id,
                    CASE
                        WHEN t.prev_close IS NULL OR t.prev_close = 0 THEN NULL
                        ELSE ROUND(((t.close_price - t.prev_close) / t.prev_close) * 100.0, 2)
                    END AS computed_return_1d
                FROM (
                    SELECT
                        id,
                        close_price,
                        LAG(close_price) OVER (PARTITION BY stock_id ORDER BY trading_date) AS prev_close
                    FROM stock_daily_ohlc
                ) t
            )
            UPDATE stock_daily_ohlc o
            SET
                return_1d = c.computed_return_1d,
                updated_at = NOW()
            FROM computed c
            WHERE o.id = c.id
              AND o.return_1d IS DISTINCT FROM c.computed_return_1d
            """
        )
        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            result = connection.execute(sql)
        return int(result.rowcount or 0)

    def _is_ohlc_ready_for_metrics(self) -> bool:
        sql = text(
            """
            WITH latest_rows AS (
                SELECT
                    o.stock_id,
                    o.return_1d,
                    cnt.row_count
                FROM stock_daily_ohlc o
                JOIN (
                    SELECT stock_id, MAX(trading_date) AS latest_trading_date
                    FROM stock_daily_ohlc
                    GROUP BY stock_id
                ) mx
                  ON mx.stock_id = o.stock_id
                 AND mx.latest_trading_date = o.trading_date
                JOIN (
                    SELECT stock_id, COUNT(*) AS row_count
                    FROM stock_daily_ohlc
                    GROUP BY stock_id
                ) cnt ON cnt.stock_id = o.stock_id
            )
            SELECT COUNT(*) FILTER (WHERE row_count > 1 AND return_1d IS NULL) AS missing_count
            FROM latest_rows
            """
        )
        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            missing_count = int(connection.execute(sql).scalar_one() or 0)
        return missing_count == 0

    def sync_stocks_replica(self) -> int:
        if not bool(getattr(self._settings, "stock_replica_sync_enabled", False)):
            return 0

        base_url = getattr(self._settings, "stock_service_base_url", "http://stock-service:4003")
        timeout_seconds = float(getattr(self._settings, "stock_service_timeout_seconds", 30))

        with httpx.Client(base_url=base_url, timeout=timeout_seconds) as client:
            response = client.get("/stocks")
            response.raise_for_status()
            payload = response.json()

        rows: list[dict[str, Any]] = []
        for item in payload:
            try:
                stock_id = int(item["id"])
                ticker = str(item["symbol"]).strip().upper()
            except (KeyError, TypeError, ValueError):
                continue

            if not ticker:
                continue

            rows.append(
                {
                    "stock_id": stock_id,
                    "ticker": ticker,
                    "name": str(item.get("name") or "").strip() or None,
                    "market": str(item.get("market") or "").strip() or None,
                    "market_cap": _to_optional_float(item.get("marketCap", item.get("market_cap"))),
                }
            )

        if not rows:
            return 0

        upsert = text(
            """
            INSERT INTO stocks (stock_id, ticker, name, market, market_cap, updated_at)
            VALUES (:stock_id, :ticker, :name, :market, :market_cap, NOW())
            ON CONFLICT (stock_id)
            DO UPDATE SET
                ticker = EXCLUDED.ticker,
                name = EXCLUDED.name,
                market = EXCLUDED.market,
                market_cap = COALESCE(EXCLUDED.market_cap, stocks.market_cap),
                updated_at = NOW()
            """
        )

        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            connection.execute(upsert, rows)

        return len(rows)

    def sync_missing_ohlc(self) -> tuple[int, date | None, date | None]:
        api_key = getattr(self._settings, "massive_api_key", "")
        if not api_key:
            logger.warning("Skipping OHLC sync because MASSIVE_API_KEY is not configured.")
            return 0, None, None

        years_back = int(getattr(self._settings, "ohlc_years_back", 3))
        retention_buffer_days = int(getattr(self._settings, "ohlc_retention_buffer_days", 90))
        adjusted = bool(getattr(self._settings, "ohlc_adjusted", True))
        include_otc = bool(getattr(self._settings, "ohlc_include_otc", False))
        base_url = getattr(self._settings, "massive_api_base_url", "https://api.massive.com")

        end_date = datetime.now(timezone.utc).date()
        configured_start = end_date - timedelta(days=max(1, years_back) * 365)
        retention_cutoff = end_date - timedelta(days=max(1, years_back) * 365 + max(0, retention_buffer_days))

        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            max_date = connection.execute(text("SELECT MAX(trading_date) FROM stock_daily_ohlc")).scalar_one_or_none()
            ticker_rows = connection.execute(text("SELECT stock_id, ticker FROM stocks")).mappings().all()

        start_date = (max_date + timedelta(days=1)) if max_date else configured_start
        if start_date < configured_start:
            start_date = configured_start

        if start_date > end_date:
            return 0, None, None

        stock_id_by_ticker = {
            str(row["ticker"]).strip().upper(): int(row["stock_id"])
            for row in ticker_rows
            if row["ticker"] and row["stock_id"] is not None
        }

        if not stock_id_by_ticker:
            return 0, None, None

        upsert_sql = text(
            """
            INSERT INTO stock_daily_ohlc (
                stock_id, trading_date, open_price, high_price, low_price, close_price, volume, updated_at
            ) VALUES (
                :stock_id, :trading_date, :open_price, :high_price, :low_price, :close_price, :volume, NOW()
            )
            ON CONFLICT (stock_id, trading_date)
            DO UPDATE SET
                open_price = EXCLUDED.open_price,
                high_price = EXCLUDED.high_price,
                low_price = EXCLUDED.low_price,
                close_price = EXCLUDED.close_price,
                volume = EXCLUDED.volume,
                updated_at = NOW()
            """
        )

        total_rows = 0
        first_synced: date | None = None
        last_synced: date | None = None

        with httpx.Client(base_url=base_url, timeout=60.0) as client:
            cursor = start_date
            while cursor <= end_date:
                if cursor.weekday() >= 5:
                    cursor += timedelta(days=1)
                    continue

                params = {
                    "adjusted": str(adjusted).lower(),
                    "include_otc": str(include_otc).lower(),
                    "apiKey": api_key,
                }
                path = f"/v2/aggs/grouped/locale/us/market/stocks/{cursor.isoformat()}"

                try:
                    response = client.get(path, params=params)
                    if response.status_code == 404:
                        cursor += timedelta(days=1)
                        continue
                    response.raise_for_status()
                except Exception as exc:  # pragma: no cover - external dependency
                    logger.warning("Failed OHLC fetch for %s: %s", cursor, exc)
                    cursor += timedelta(days=1)
                    continue

                data = response.json()
                results = data.get("results") if isinstance(data, dict) else None
                if not isinstance(results, list) or not results:
                    cursor += timedelta(days=1)
                    continue

                rows: list[dict[str, Any]] = []
                for node in results:
                    if not isinstance(node, dict):
                        continue
                    ticker = str(node.get("T") or "").strip().upper()
                    stock_id = stock_id_by_ticker.get(ticker)
                    if stock_id is None:
                        continue

                    open_price = _to_rounded(node.get("o"))
                    high_price = _to_rounded(node.get("h"))
                    low_price = _to_rounded(node.get("l"))
                    close_price = _to_rounded(node.get("c"))
                    if None in (open_price, high_price, low_price, close_price):
                        continue

                    rows.append(
                        {
                            "stock_id": stock_id,
                            "trading_date": cursor,
                            "open_price": open_price,
                            "high_price": high_price,
                            "low_price": low_price,
                            "close_price": close_price,
                            "volume": int(round(float(node.get("v") or 0))),
                        }
                    )

                if rows:
                    with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
                        connection.execute(upsert_sql, rows)
                    total_rows += len(rows)
                    first_synced = first_synced or cursor
                    last_synced = cursor

                cursor += timedelta(days=1)

        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            connection.execute(
                text(
                    """
                    DELETE FROM stock_daily_ohlc
                    WHERE trading_date < :retention_cutoff
                    """
                ),
                {"retention_cutoff": retention_cutoff},
            )

        return total_rows, first_synced, last_synced

    def refresh_metrics_with_pyspark(self) -> tuple[int, int]:
        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            base_frame = pd.read_sql_query(
                text(
                    """
                    SELECT
                        id,
                        stock_id,
                        trading_date,
                        open_price,
                        high_price,
                        low_price,
                        close_price,
                        volume,
                        return_1d
                    FROM stock_daily_ohlc
                    ORDER BY stock_id, trading_date
                    """
                ),
                connection,
            )

        if base_frame.empty:
            return 0, 0

        base_frame["trading_date"] = pd.to_datetime(base_frame["trading_date"]).dt.date

        # Spark needs a JVM; auto-wire JAVA_HOME/PATH when Java is installed but not exported.
        ensure_java_runtime(logger)

        try:
            spark = (
                SparkSession.builder.master("local[*]")
                .appName("tradepulse-analytics-nightly")
                .config("spark.ui.enabled", "false")
                .getOrCreate()
            )
        except Exception as exc:
            raise RuntimeError(
                "PySpark could not start because Java was not found. "
                "Install JDK 11+ and set JAVA_HOME (or add java to PATH)."
            ) from exc

        try:
            sdf = spark.createDataFrame(base_frame.to_dict("records"))
            # pandas reads NULL NUMERIC columns as float NaN; Spark createDataFrame preserves
            # those as Spark NaN (not null), so isNotNull() won't filter them and AVG(NaN)=NaN
            # which causes NOT NULL constraint violations.  Coerce NaN → null explicitly.
            sdf = sdf.withColumn(
                "return_1d",
                F.when(
                    F.col("return_1d").isNull() | F.isnan(F.col("return_1d")),
                    F.lit(None).cast("double"),
                ).otherwise(F.col("return_1d").cast("double")),
            )
            w = Window.partitionBy("stock_id").orderBy("trading_date")
            vol10_w = w.rowsBetween(-9, 0)

            def rolling_avg(col: str, days: int):
                frame = Window.partitionBy("stock_id").orderBy("trading_date").rowsBetween(-(days - 1), 0)
                return F.when(F.count(F.col(col)).over(frame) == days, F.avg(F.col(col)).over(frame))

            def rolling_std(col: str, days: int):
                frame = Window.partitionBy("stock_id").orderBy("trading_date").rowsBetween(-(days - 1), 0)
                return F.when(F.count(F.col(col)).over(frame) == days, F.stddev_samp(F.col(col)).over(frame))

            prev_close = F.lag("close_price", 1).over(w)
            # return_1d is read directly from stock_daily_ohlc (NUMERIC(12,2), already 2dp).
            # No need to recompute — use stored value so avg_return/volatility in
            # ml_weekly_features are derived from exactly the same number that's in the DB.
            daily_return_ratio = ((F.col("close_price") - prev_close) / prev_close)

            feat = (
                sdf  # return_1d already present from the SQL SELECT
                .withColumn("sma_20", rolling_avg("close_price", 20))
                .withColumn("sma_50", rolling_avg("close_price", 50))
                .withColumn("sma_200", rolling_avg("close_price", 200))
                .withColumn("volatility_5d", rolling_std("return_1d", 5))
                .withColumn("volatility_20d", rolling_std("return_1d", 20))
                .withColumn("volatility_60d", rolling_std("return_1d", 60))
                .withColumn("volatility_90d", rolling_std("return_1d", 90))
                .withColumn("volatility_120d", rolling_std("return_1d", 120))
                .withColumn("return_5d", ((F.col("close_price") - F.lag("close_price", 5).over(w)) / F.lag("close_price", 5).over(w)) * 100)
                .withColumn("return_10d", ((F.col("close_price") - F.lag("close_price", 10).over(w)) / F.lag("close_price", 10).over(w)) * 100)
                .withColumn("return_20d", ((F.col("close_price") - F.lag("close_price", 20).over(w)) / F.lag("close_price", 20).over(w)) * 100)
                .withColumn("ret_21d", ((F.col("close_price") - F.lag("close_price", 21).over(w)) / F.lag("close_price", 21).over(w)) * 100)
                .withColumn("ret_63d", ((F.col("close_price") - F.lag("close_price", 63).over(w)) / F.lag("close_price", 63).over(w)) * 100)
                .withColumn("ret_126d", ((F.col("close_price") - F.lag("close_price", 126).over(w)) / F.lag("close_price", 126).over(w)) * 100)
                .withColumn("ret_252d", ((F.col("close_price") - F.lag("close_price", 252).over(w)) / F.lag("close_price", 252).over(w)) * 100)
                .withColumn("ret_756d", ((F.col("close_price") - F.lag("close_price", 756).over(w)) / F.lag("close_price", 756).over(w)) * 100)
                .withColumn(
                    "volatility_10d",
                    F.when(
                        F.count(F.col("return_1d")).over(vol10_w) == 10,
                        F.stddev_samp(F.col("return_1d")).over(vol10_w),
                    ),
                )
                .withColumn("delta", F.col("close_price") - prev_close)
                .withColumn("gain", F.when(F.col("delta") > 0, F.col("delta")).otherwise(F.lit(0.0)))
                .withColumn("loss", F.when(F.col("delta") < 0, -F.col("delta")).otherwise(F.lit(0.0)))
                .withColumn("avg_gain_14", rolling_avg("gain", 14))
                .withColumn("avg_loss_14", rolling_avg("loss", 14))
                .withColumn(
                    "rsi_14",
                    F.when(F.col("avg_loss_14").isNull(), F.lit(None).cast("double"))
                    .when(F.col("avg_loss_14") == 0, F.lit(100.0))
                    .otherwise(100 - (100 / (1 + (F.col("avg_gain_14") / F.col("avg_loss_14"))))),
                )
                .withColumn("sma_12", rolling_avg("close_price", 12))
                .withColumn("sma_26", rolling_avg("close_price", 26))
                .withColumn("macd", F.col("sma_12") - F.col("sma_26"))
                .withColumn("macd_signal", rolling_avg("macd", 9))
                .withColumn("sma20_distance", ((F.col("close_price") - F.col("sma_20")) / F.col("sma_20")) * 100)
                .withColumn("sma50_distance", ((F.col("close_price") - F.col("sma_50")) / F.col("sma_50")) * 100)
                .withColumn("volume_prev", F.lag("volume", 1).over(w))
                .withColumn("volume_change", ((F.col("volume") - F.col("volume_prev")) / F.col("volume_prev")) * 100)
                .withColumn("label", F.when(F.col("return_1d") > 0, F.lit(1)).otherwise(F.lit(0)))
                .withColumn("daily_return_ratio", daily_return_ratio)
            )

            # Persist per-row chart indicators back into OHLC table (only columns plotted in historical charts).
            ohlc_updates = feat.select(
                "id",
                "sma_20",
                "sma_50",
                "sma_200",
                "volatility_20d",
                "volatility_60d",
                "volatility_90d",
            ).toPandas()

            ohlc_updated_rows = self._persist_ohlc_updates(ohlc_updates)

            latest_w = Window.partitionBy("stock_id").orderBy(F.col("trading_date").desc())

            win_252 = Window.partitionBy("stock_id").orderBy("trading_date").rowsBetween(-251, 0)
            win_30 = Window.partitionBy("stock_id").orderBy("trading_date").rowsBetween(-29, 0)
            win_252_ret = Window.partitionBy("stock_id").orderBy("trading_date").rowsBetween(-251, 0)

            metrics_feat = (
                feat.withColumn("high_52w", F.max("high_price").over(win_252))
                .withColumn("low_52w", F.min("low_price").over(win_252))
                .withColumn("avg_volume_30d", F.avg("volume").over(win_30))
                # Null return_1d (first row per stock) must not be classified as flat — use isNull guard.
                .withColumn(
                    "daily_sign",
                    F.when(F.col("return_1d").isNull(), F.lit(None).cast("integer"))
                    .when(F.col("return_1d") > 0, F.lit(1))
                    .when(F.col("return_1d") < 0, F.lit(-1))
                    .otherwise(F.lit(0)),
                )
                .withColumn("positive_days_1y", F.sum(F.when(F.col("daily_sign") > 0, 1).otherwise(0)).over(win_252))
                .withColumn("negative_days_1y", F.sum(F.when(F.col("daily_sign") < 0, 1).otherwise(0)).over(win_252))
                .withColumn("flat_days_1y", F.sum(F.when(F.col("daily_sign") == 0, 1).otherwise(0)).over(win_252))
                .withColumn("mean_ret_252", F.avg("daily_return_ratio").over(win_252_ret))
                .withColumn("std_ret_252", F.stddev_samp("daily_return_ratio").over(win_252_ret))
                .withColumn("downside_only", F.when(F.col("daily_return_ratio") < 0, F.col("daily_return_ratio")))
                .withColumn("downside_std_252", F.stddev_samp("downside_only").over(win_252_ret))
                .withColumn("sharpe_ratio", (F.col("mean_ret_252") / F.col("std_ret_252")) * math.sqrt(252.0))
                .withColumn("sortino_ratio", (F.col("mean_ret_252") / F.col("downside_std_252")) * math.sqrt(252.0))
                .withColumn("distance_from_high_percent", ((F.col("close_price") - F.col("high_52w")) / F.col("high_52w")) * 100)
                .withColumn("distance_from_low_percent", ((F.col("close_price") - F.col("low_52w")) / F.col("low_52w")) * 100)
                .withColumn("relative_volume", F.col("volume") / F.col("avg_volume_30d"))
                .withColumn("golden_cross", F.col("sma_50") > F.col("sma_200"))
                .withColumn("death_cross", F.col("sma_50") < F.col("sma_200"))
            )

            metrics_latest = (
                metrics_feat.withColumn("rn", F.row_number().over(latest_w))
                .where(F.col("rn") == 1)
                .select(
                    "stock_id",
                    F.col("return_5d").alias("week_return"),
                    F.col("ret_21d").alias("month_return"),
                    F.col("ret_63d").alias("three_month_return"),
                    F.col("ret_126d").alias("six_month_return"),
                    F.col("ret_252d").alias("year_return"),
                    F.col("ret_756d").alias("three_year_return"),
                    "volatility_5d",
                    "volatility_20d",
                    "volatility_60d",
                    "volatility_90d",
                    "volatility_120d",
                    "return_5d",
                    "return_10d",
                    "return_20d",
                    "volatility_10d",
                    "sma20_distance",
                    "sma50_distance",
                    "rsi_14",
                    "macd",
                    "macd_signal",
                    "high_52w",
                    "low_52w",
                    "distance_from_high_percent",
                    "distance_from_low_percent",
                    "avg_volume_30d",
                    F.col("volume").alias("latest_trading_day_volume"),
                    F.col("trading_date").alias("latest_trading_date"),
                    "volume_change",
                    "label",
                    "relative_volume",
                    "positive_days_1y",
                    "negative_days_1y",
                    "flat_days_1y",
                    "sharpe_ratio",
                    "sortino_ratio",
                    "golden_cross",
                    "death_cross",
                )
            ).toPandas()

            extras = _compute_pandas_extras(base_frame)
            metric_rows = _merge_metric_rows(metrics_latest, extras)
            upserted_metric_count = self._upsert_metrics(metric_rows)
            self._refresh_latest_news_for_metrics()

            weekly_rows = self._upsert_weekly_features_from_metrics_snapshot()

            return max(ohlc_updated_rows, upserted_metric_count), weekly_rows
        finally:
            spark.stop()

    def _persist_ohlc_updates(self, updates: pd.DataFrame) -> int:
        if updates.empty:
            return 0

        sql = text(
            """
            UPDATE stock_daily_ohlc
            SET
                sma_20 = :sma_20,
                sma_50 = :sma_50,
                sma_200 = :sma_200,
                volatility_20d = :volatility_20d,
                volatility_60d = :volatility_60d,
                volatility_90d = :volatility_90d,
                updated_at = NOW()
            WHERE id = :id
            """
        )

        records = updates.where(pd.notna(updates), None).to_dict("records")
        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            for chunk in _chunks(records, 2000):
                connection.execute(sql, chunk)

        return len(records)

    def _upsert_metrics(self, rows: list[dict[str, Any]]) -> int:
        if not rows:
            return 0

        sql = text(
            """
            INSERT INTO stock_metrics (
                stock_id,
                week_return,
                month_return,
                three_month_return,
                six_month_return,
                year_return,
                three_year_return,
                volatility_5d,
                volatility_20d,
                volatility_60d,
                volatility_90d,
                volatility_120d,
                return_5d,
                return_10d,
                return_20d,
                volatility_10d,
                sma20_distance,
                sma50_distance,
                rsi_14,
                macd,
                macd_signal,
                high_52w,
                low_52w,
                distance_from_high_percent,
                distance_from_low_percent,
                avg_volume_30d,
                latest_trading_day_volume,
                latest_trading_date,
                volume_change,
                label,
                relative_volume,
                positive_days_1y,
                negative_days_1y,
                flat_days_1y,
                monthly_returns_heatmap,
                max_drawdown,
                drawdown_peak_date,
                drawdown_trough_date,
                sharpe_ratio,
                sortino_ratio,
                golden_cross,
                death_cross,
                latest_news,
                updated_at
            ) VALUES (
                :stock_id,
                :week_return,
                :month_return,
                :three_month_return,
                :six_month_return,
                :year_return,
                :three_year_return,
                :volatility_5d,
                :volatility_20d,
                :volatility_60d,
                :volatility_90d,
                :volatility_120d,
                :return_5d,
                :return_10d,
                :return_20d,
                :volatility_10d,
                :sma20_distance,
                :sma50_distance,
                :rsi_14,
                :macd,
                :macd_signal,
                :high_52w,
                :low_52w,
                :distance_from_high_percent,
                :distance_from_low_percent,
                :avg_volume_30d,
                :latest_trading_day_volume,
                :latest_trading_date,
                :volume_change,
                :label,
                :relative_volume,
                :positive_days_1y,
                :negative_days_1y,
                :flat_days_1y,
                :monthly_returns_heatmap,
                :max_drawdown,
                :drawdown_peak_date,
                :drawdown_trough_date,
                :sharpe_ratio,
                :sortino_ratio,
                :golden_cross,
                :death_cross,
                :latest_news,
                NOW()
            )
            ON CONFLICT (stock_id)
            DO UPDATE SET
                week_return = EXCLUDED.week_return,
                month_return = EXCLUDED.month_return,
                three_month_return = EXCLUDED.three_month_return,
                six_month_return = EXCLUDED.six_month_return,
                year_return = EXCLUDED.year_return,
                three_year_return = EXCLUDED.three_year_return,
                volatility_5d = EXCLUDED.volatility_5d,
                volatility_20d = EXCLUDED.volatility_20d,
                volatility_60d = EXCLUDED.volatility_60d,
                volatility_90d = EXCLUDED.volatility_90d,
                volatility_120d = EXCLUDED.volatility_120d,
                return_5d = EXCLUDED.return_5d,
                return_10d = EXCLUDED.return_10d,
                return_20d = EXCLUDED.return_20d,
                volatility_10d = EXCLUDED.volatility_10d,
                sma20_distance = EXCLUDED.sma20_distance,
                sma50_distance = EXCLUDED.sma50_distance,
                rsi_14 = EXCLUDED.rsi_14,
                macd = EXCLUDED.macd,
                macd_signal = EXCLUDED.macd_signal,
                high_52w = EXCLUDED.high_52w,
                low_52w = EXCLUDED.low_52w,
                distance_from_high_percent = EXCLUDED.distance_from_high_percent,
                distance_from_low_percent = EXCLUDED.distance_from_low_percent,
                avg_volume_30d = EXCLUDED.avg_volume_30d,
                latest_trading_day_volume = EXCLUDED.latest_trading_day_volume,
                latest_trading_date = EXCLUDED.latest_trading_date,
                volume_change = EXCLUDED.volume_change,
                label = EXCLUDED.label,
                relative_volume = EXCLUDED.relative_volume,
                positive_days_1y = EXCLUDED.positive_days_1y,
                negative_days_1y = EXCLUDED.negative_days_1y,
                flat_days_1y = EXCLUDED.flat_days_1y,
                monthly_returns_heatmap = EXCLUDED.monthly_returns_heatmap,
                max_drawdown = EXCLUDED.max_drawdown,
                drawdown_peak_date = EXCLUDED.drawdown_peak_date,
                drawdown_trough_date = EXCLUDED.drawdown_trough_date,
                sharpe_ratio = EXCLUDED.sharpe_ratio,
                sortino_ratio = EXCLUDED.sortino_ratio,
                golden_cross = EXCLUDED.golden_cross,
                death_cross = EXCLUDED.death_cross,
                latest_news = COALESCE(EXCLUDED.latest_news, stock_metrics.latest_news),
                prediction_action = NULL,
                prediction_confidence = NULL,
                prediction_probability_buy = NULL,
                prediction_probability_sell = NULL,
                prediction_confidence_edge = NULL,
                prediction_probability_gap = NULL,
                prediction_conviction_label = NULL,
                prediction_reasoning = NULL,
                prediction_model_version = NULL,
                prediction_horizon_days = NULL,
                prediction_decision_threshold = NULL,
                prediction_generated_at = NULL,
                updated_at = NOW()
            """
        )

        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            connection.execute(sql, rows)

        return len(rows)

    def _refresh_latest_news_for_metrics(self) -> int:
        api_key = getattr(self._settings, "massive_api_key", "")
        if not api_key:
            return 0

        base_url = getattr(self._settings, "massive_api_base_url", "https://api.massive.com")
        max_news_per_stock = 3
        max_pages = max(1, int(getattr(self._settings, "massive_news_max_pages", 10)))
        page_size = 1000  # Polygon maximum per request

        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            stock_rows = connection.execute(
                text("SELECT stock_id, ticker, name FROM stocks")
            ).mappings().all()

        if not stock_rows:
            return 0

        stock_id_by_ticker: dict[str, int] = {}
        stock_name_by_ticker: dict[str, str] = {}
        stock_terms_by_ticker: dict[str, list[str]] = {}
        for row in stock_rows:
            if not row["ticker"] or row["stock_id"] is None:
                continue
            ticker = str(row["ticker"]).strip().upper()
            stock_name = str(row["name"] or "").strip()
            stock_id_by_ticker[ticker] = int(row["stock_id"])
            stock_name_by_ticker[ticker] = _normalize_news_text(stock_name)
            stock_terms_by_ticker[ticker] = _extract_stock_name_terms(stock_name)

        ranked_articles: dict[str, list[tuple[int, int, dict[str, Any]]]] = {t: [] for t in stock_id_by_ticker}
        seen_urls_by_ticker: dict[str, set[str]] = {t: set() for t in stock_id_by_ticker}

        upsert_sql = text(
            """
            INSERT INTO stock_metrics (stock_id, latest_news, updated_at)
            VALUES (:stock_id, :latest_news, NOW())
            ON CONFLICT (stock_id)
            DO UPDATE SET
                latest_news = EXCLUDED.latest_news,
                updated_at = NOW()
            """
        )

        with httpx.Client(base_url=base_url, timeout=30.0) as client:
            next_url: str | None = None
            sequence = 0
            params: dict[str, Any] = {
                "limit": page_size,
                "order": "desc",
                "sort": "published_utc",
                "apiKey": api_key,
            }

            for page in range(max_pages):
                try:
                    if next_url:
                        # next_url is an absolute URL returned by Polygon; use it directly.
                        response = client.get(next_url, params={"apiKey": api_key})
                    else:
                        response = client.get("/v2/reference/news", params=params)
                    response.raise_for_status()
                except Exception as exc:  # pragma: no cover - external dependency
                    logger.warning("Failed batch news fetch (page %d): %s", page, exc)
                    break

                payload = response.json()
                results = payload.get("results") if isinstance(payload, dict) else None
                if not isinstance(results, list) or not results:
                    break

                for item in results:
                    if not isinstance(item, dict):
                        continue
                    publisher = item.get("publisher")
                    publisher_name = publisher.get("name") if isinstance(publisher, dict) else None
                    title = str(item.get("title") or "")
                    description = str(item.get("description") or "")
                    article_url = str(item.get("article_url") or "").strip() or None
                    article_tickers = [str(t).strip().upper() for t in (item.get("tickers") or []) if str(t).strip()]
                    compact_item = {
                        "headline": item.get("title"),
                        "publishedUtc": item.get("published_utc"),
                        "url": article_url,
                        "publisher": publisher_name,
                        "description": item.get("description"),
                    }
                    for ticker in article_tickers:
                        if ticker not in stock_id_by_ticker:
                            continue
                        if article_url and article_url in seen_urls_by_ticker[ticker]:
                            continue
                        score = _score_stock_news_relevance(
                            ticker=ticker,
                            stock_name=stock_name_by_ticker.get(ticker, ""),
                            stock_terms=stock_terms_by_ticker.get(ticker, []),
                            title=title,
                            description=description,
                            article_tickers=article_tickers,
                        )
                        ranked_articles[ticker].append((score, sequence, compact_item))
                        if article_url:
                            seen_urls_by_ticker[ticker].add(article_url)
                    sequence += 1

                if all(_count_priority_candidates(ranked_articles[t]) >= max_news_per_stock for t in stock_id_by_ticker):
                    break

                next_url = payload.get("next_url")
                if not next_url:
                    break

        updates: list[dict[str, Any]] = []
        for ticker, stock_id in stock_id_by_ticker.items():
            ranked = sorted(ranked_articles[ticker], key=lambda article: (-article[0], article[1]))
            merged = [article[2] for article in ranked[:max_news_per_stock]]
            updates.append(
                {
                    "stock_id": stock_id,
                    "latest_news": json.dumps(merged) if merged else None,
                }
            )

        if not updates:
            return 0

        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            for chunk in _chunks(updates, 500):
                connection.execute(upsert_sql, chunk)

        return len(updates)

    def _upsert_weekly_features_from_metrics_snapshot(self) -> int:
        sql = text(
            """
            WITH ranked_stocks AS (
                SELECT stock_id
                FROM stocks
                ORDER BY COALESCE(market_cap, 0) DESC, stock_id ASC
                LIMIT 50
            ),
            inserted AS (
                INSERT INTO ml_weekly_features (
                    stock_id,
                    date,
                    return_5d,
                    return_10d,
                    return_20d,
                    volatility_5d,
                    volatility_10d,
                    volatility_20d,
                    sma20_distance,
                    sma50_distance,
                    rsi,
                    macd,
                    volume_change,
                    label,
                    created_at
                )
                SELECT
                    m.stock_id,
                    m.latest_trading_date AS date,
                    m.return_5d,
                    m.return_10d,
                    m.return_20d,
                    m.volatility_5d,
                    m.volatility_10d,
                    m.volatility_20d,
                    m.sma20_distance,
                    m.sma50_distance,
                    m.rsi_14,
                    m.macd,
                    m.volume_change,
                    m.label,
                    NOW()
                FROM stock_metrics m
                JOIN ranked_stocks rs ON rs.stock_id = m.stock_id
                WHERE m.latest_trading_date IS NOT NULL
                  AND m.return_5d IS NOT NULL
                  AND m.return_10d IS NOT NULL
                  AND m.return_20d IS NOT NULL
                  AND m.volatility_5d IS NOT NULL
                  AND m.volatility_10d IS NOT NULL
                  AND m.volatility_20d IS NOT NULL
                  AND m.sma20_distance IS NOT NULL
                  AND m.sma50_distance IS NOT NULL
                  AND m.rsi_14 IS NOT NULL
                  AND m.macd IS NOT NULL
                  AND m.volume_change IS NOT NULL
                  AND m.label IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM ml_weekly_features w
                      WHERE w.stock_id = m.stock_id
                        AND date_trunc('week', w.date) = date_trunc('week', m.latest_trading_date)
                  )
                ON CONFLICT (stock_id, date)
                DO UPDATE SET
                    return_5d = EXCLUDED.return_5d,
                    return_10d = EXCLUDED.return_10d,
                    return_20d = EXCLUDED.return_20d,
                    volatility_5d = EXCLUDED.volatility_5d,
                    volatility_10d = EXCLUDED.volatility_10d,
                    volatility_20d = EXCLUDED.volatility_20d,
                    sma20_distance = EXCLUDED.sma20_distance,
                    sma50_distance = EXCLUDED.sma50_distance,
                    rsi = EXCLUDED.rsi,
                    macd = EXCLUDED.macd,
                    volume_change = EXCLUDED.volume_change,
                    label = EXCLUDED.label
                RETURNING 1
            )
            SELECT COUNT(*) FROM inserted
            """
        )

        with self._repository._engine.begin() as connection:  # pylint: disable=protected-access
            inserted_rows = int(connection.execute(sql).scalar() or 0)
            connection.execute(text("DELETE FROM ml_weekly_features WHERE date < CURRENT_DATE - INTERVAL '365 days'"))
            connection.execute(
                text(
                    """
                    DELETE FROM ml_weekly_features
                    WHERE stock_id NOT IN (
                        SELECT stock_id
                        FROM stocks
                        ORDER BY COALESCE(market_cap, 0) DESC, stock_id ASC
                        LIMIT 50
                    )
                    """
                )
            )

        return inserted_rows


def _to_rounded(value: Any) -> float | None:
    try:
        if value is None or pd.isna(value):
            return None
        return round(float(value), 2)
    except (TypeError, ValueError):
        return None


def _to_optional_float(value: Any) -> float | None:
    try:
        if value is None or pd.isna(value):
            return None
        return float(value)
    except (TypeError, ValueError):
        return None


def _chunks(values: list[dict[str, Any]], size: int):
    for index in range(0, len(values), size):
        yield values[index : index + size]


_NEWS_TEXT_SPLIT_RE = re.compile(r"[^a-z0-9]+")
_CORPORATE_NAME_STOPWORDS = {
    "a",
    "an",
    "and",
    "class",
    "co",
    "company",
    "corp",
    "corporation",
    "group",
    "holdings",
    "inc",
    "incorporated",
    "limited",
    "ltd",
    "n",
    "nv",
    "plc",
    "sa",
    "series",
    "the",
}
_PRIORITY_NEWS_SCORE = 4


def _normalize_news_text(value: Any) -> str:
    text_value = str(value or "").lower().strip()
    if not text_value:
        return ""
    return " ".join(part for part in _NEWS_TEXT_SPLIT_RE.split(text_value) if part)


def _extract_stock_name_terms(stock_name: str) -> list[str]:
    normalized = _normalize_news_text(stock_name)
    if not normalized:
        return []
    terms: list[str] = []
    for part in normalized.split():
        if len(part) < 3 or part in _CORPORATE_NAME_STOPWORDS:
            continue
        if part not in terms:
            terms.append(part)
    return terms


def _contains_normalized_phrase(text: str, phrase: str) -> bool:
    if not text or not phrase:
        return False
    padded_text = f" {text} "
    padded_phrase = f" {phrase} "
    return padded_phrase in padded_text


def _score_stock_news_relevance(
    *,
    ticker: str,
    stock_name: str,
    stock_terms: list[str],
    title: str,
    description: str,
    article_tickers: list[str],
) -> int:
    normalized_ticker = _normalize_news_text(ticker)
    normalized_title = _normalize_news_text(title)
    normalized_description = _normalize_news_text(description)
    combined = f"{normalized_title} {normalized_description}".strip()

    score = 0
    if stock_name:
        if _contains_normalized_phrase(normalized_title, stock_name):
            score += 6
        elif _contains_normalized_phrase(normalized_description, stock_name):
            score += 4

    if normalized_ticker:
        if _contains_normalized_phrase(normalized_title, normalized_ticker):
            score += 4
        elif _contains_normalized_phrase(normalized_description, normalized_ticker):
            score += 2

    matching_terms = 0
    for term in stock_terms:
        if _contains_normalized_phrase(combined, term):
            matching_terms += 1
    score += min(matching_terms, 3) * 2

    if article_tickers == [ticker]:
        score += 2
    elif ticker in article_tickers and len(article_tickers) <= 3:
        score += 1

    return score


def _count_priority_candidates(candidates: list[tuple[int, int, dict[str, Any]]]) -> int:
    return sum(1 for score, _, _ in candidates if score >= _PRIORITY_NEWS_SCORE)


def _compute_pandas_extras(base_frame: pd.DataFrame) -> dict[int, dict[str, Any]]:
    extras: dict[int, dict[str, Any]] = {}
    frame = base_frame.sort_values(["stock_id", "trading_date"]).copy()

    for stock_id, group in frame.groupby("stock_id"):
        g = group.reset_index(drop=True)
        closes = g["close_price"].astype(float)
        dates = pd.to_datetime(g["trading_date"])

        rolling_peak = closes.cummax()
        drawdown = ((closes - rolling_peak) / rolling_peak) * 100.0
        trough_idx = int(drawdown.idxmin()) if not drawdown.empty else -1
        max_dd = float(drawdown.min()) if not drawdown.empty else None

        peak_date = None
        trough_date = None
        if trough_idx >= 0:
            peak_value = float(rolling_peak.iloc[trough_idx])
            peak_candidates = closes.iloc[: trough_idx + 1]
            peak_idx = int(peak_candidates[peak_candidates == peak_value].index[0]) if not peak_candidates.empty else trough_idx
            peak_date = dates.iloc[peak_idx].date()
            trough_date = dates.iloc[trough_idx].date()

        month_close = g[["trading_date", "close_price"]].copy()
        month_close["month"] = pd.to_datetime(month_close["trading_date"]).dt.to_period("M")
        month_agg = month_close.groupby("month")["close_price"].last().reset_index()
        month_agg["prev"] = month_agg["close_price"].shift(1)
        month_agg["return_pct"] = ((month_agg["close_price"] - month_agg["prev"]) / month_agg["prev"]) * 100

        heatmap: list[dict[str, Any]] = []
        for _, row in month_agg.dropna(subset=["return_pct"]).iterrows():
            period = row["month"]
            heatmap.append(
                {
                    "year": int(period.year),
                    "month": int(period.month),
                    "returnPercent": round(float(row["return_pct"]), 2),
                }
            )

        extras[int(stock_id)] = {
            "max_drawdown": round(max_dd, 2) if max_dd is not None else None,
            "drawdown_peak_date": peak_date,
            "drawdown_trough_date": trough_date,
            "monthly_returns_heatmap": json.dumps(heatmap) if heatmap else None,
        }

    return extras


def _merge_metric_rows(metrics_latest: pd.DataFrame, extras: dict[int, dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    if metrics_latest.empty:
        return rows

    for _, row in metrics_latest.iterrows():
        stock_id = int(row["stock_id"])
        extra = extras.get(stock_id, {})

        rows.append(
            {
                "stock_id": stock_id,
                "week_return": _round_or_none(row.get("week_return"), 2),
                "month_return": _round_or_none(row.get("month_return"), 2),
                "three_month_return": _round_or_none(row.get("three_month_return"), 2),
                "six_month_return": _round_or_none(row.get("six_month_return"), 2),
                "year_return": _round_or_none(row.get("year_return"), 2),
                "three_year_return": _round_or_none(row.get("three_year_return"), 2),
                "volatility_5d": _round_or_none(row.get("volatility_5d"), 2),
                "volatility_20d": _round_or_none(row.get("volatility_20d"), 2),
                "volatility_60d": _round_or_none(row.get("volatility_60d"), 2),
                "volatility_90d": _round_or_none(row.get("volatility_90d"), 2),
                "volatility_120d": _round_or_none(row.get("volatility_120d"), 2),
                "return_5d": _round_or_none(row.get("return_5d"), 2),
                "return_10d": _round_or_none(row.get("return_10d"), 2),
                "return_20d": _round_or_none(row.get("return_20d"), 2),
                "volatility_10d": _round_or_none(row.get("volatility_10d"), 2),
                "sma20_distance": _round_or_none(row.get("sma20_distance"), 2),
                "sma50_distance": _round_or_none(row.get("sma50_distance"), 2),
                "high_52w": _round_or_none(row.get("high_52w"), 2),
                "low_52w": _round_or_none(row.get("low_52w"), 2),
                "distance_from_high_percent": _round_or_none(row.get("distance_from_high_percent"), 2),
                "distance_from_low_percent": _round_or_none(row.get("distance_from_low_percent"), 2),
                "avg_volume_30d": _round_or_none(row.get("avg_volume_30d"), 2),
                "latest_trading_day_volume": _int_or_none(row.get("latest_trading_day_volume")),
                "latest_trading_date": _date_or_none(row.get("latest_trading_date")),
                "volume_change": _round_or_none(row.get("volume_change"), 2),
                "label": _int_or_none(row.get("label")),
                "relative_volume": _round_or_none(row.get("relative_volume"), 2),
                "rsi_14": _round_or_none(row.get("rsi_14"), 4),
                "macd": _round_or_none(row.get("macd"), 4),
                "macd_signal": _round_or_none(row.get("macd_signal"), 4),
                "positive_days_1y": _int_or_none(row.get("positive_days_1y")),
                "negative_days_1y": _int_or_none(row.get("negative_days_1y")),
                "flat_days_1y": _int_or_none(row.get("flat_days_1y")),
                "monthly_returns_heatmap": extra.get("monthly_returns_heatmap"),
                "max_drawdown": extra.get("max_drawdown"),
                "drawdown_peak_date": extra.get("drawdown_peak_date"),
                "drawdown_trough_date": extra.get("drawdown_trough_date"),
                "sharpe_ratio": _round_or_none(row.get("sharpe_ratio"), 2),
                "sortino_ratio": _round_or_none(row.get("sortino_ratio"), 2),
                "golden_cross": _bool_or_none(row.get("golden_cross")),
                "death_cross": _bool_or_none(row.get("death_cross")),
                "latest_news": None,
            }
        )

    return rows


def _round_or_none(value: Any, digits: int) -> float | None:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return None
    try:
        return round(float(value), digits)
    except (TypeError, ValueError):
        return None


def _int_or_none(value: Any) -> int | None:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _date_or_none(value: Any) -> date | None:
    if value is None:
        return None
    if isinstance(value, date):
        return value
    if isinstance(value, pd.Timestamp):
        return value.date()
    try:
        return pd.to_datetime(value).date()
    except Exception:  # pragma: no cover
        return None


def _bool_or_none(value: Any) -> bool | None:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return None
    return bool(value)

