from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

import numpy as np
import pandas as pd
from imblearn.over_sampling import RandomOverSampler
from imblearn.pipeline import Pipeline as ImbPipeline
from imblearn.under_sampling import RandomUnderSampler
from sklearn.compose import ColumnTransformer
from sklearn.decomposition import PCA
from sklearn.ensemble import ExtraTreesClassifier, GradientBoostingClassifier, RandomForestClassifier
from sklearn.feature_selection import SelectFromModel
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import balanced_accuracy_score, f1_score, precision_score, recall_score
from sklearn.model_selection import RandomizedSearchCV, TimeSeriesSplit
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, RobustScaler
from xgboost import XGBClassifier


NUMERIC_FEATURES = [
    "open_price",
    "high_price",
    "low_price",
    "close_price",
    "volume",
    "sma_20",
    "sma_50",
    "sma_200",
    "volatility_30d",
    "volatility_90d",
    "daily_return_percent",
    "return_1d",
    "return_5d",
    "return_10d",
    "return_20d",
    "rolling_vol_10d",
    "rolling_vol_20d",
    "volume_change_5d",
    "price_vs_sma20",
    "price_vs_sma50",
    "price_vs_sma200",
    "high_low_spread",
    "close_open_spread",
]
CATEGORICAL_FEATURES = ["market"]


@dataclass
class TrainedModelBundle:
    estimator: Any
    metrics: list[dict[str, float | str]]
    selected_model: str
    model_version: str
    horizon_days: int
    positive_return_threshold: float
    trained_rows: int


def _engineer_features(frame: pd.DataFrame, horizon_days: int, positive_return_threshold: float) -> pd.DataFrame:
    data = frame.copy()
    data["trading_date"] = pd.to_datetime(data["trading_date"])
    data = data.sort_values(["stock_id", "trading_date"]).reset_index(drop=True)

    grouped = data.groupby("stock_id", group_keys=False)

    data["return_1d"] = grouped["close_price"].pct_change(1)
    data["return_5d"] = grouped["close_price"].pct_change(5)
    data["return_10d"] = grouped["close_price"].pct_change(10)
    data["return_20d"] = grouped["close_price"].pct_change(20)

    data["rolling_vol_10d"] = grouped["daily_return_percent"].transform(lambda s: s.rolling(10).std())
    data["rolling_vol_20d"] = grouped["daily_return_percent"].transform(lambda s: s.rolling(20).std())
    data["volume_change_5d"] = grouped["volume"].pct_change(5)

    data["price_vs_sma20"] = (data["close_price"] - data["sma_20"]) / data["sma_20"].replace(0, np.nan)
    data["price_vs_sma50"] = (data["close_price"] - data["sma_50"]) / data["sma_50"].replace(0, np.nan)
    data["price_vs_sma200"] = (data["close_price"] - data["sma_200"]) / data["sma_200"].replace(0, np.nan)
    data["high_low_spread"] = (data["high_price"] - data["low_price"]) / data["close_price"].replace(0, np.nan)
    data["close_open_spread"] = (data["close_price"] - data["open_price"]) / data["open_price"].replace(0, np.nan)

    data["forward_return"] = grouped["close_price"].shift(-horizon_days) / data["close_price"] - 1.0
    data["target"] = (data["forward_return"] > positive_return_threshold).astype(int)

    return data.replace([np.inf, -np.inf], np.nan)


def build_prediction_row(frame: pd.DataFrame) -> pd.DataFrame:
    engineered = _engineer_features(frame, horizon_days=5, positive_return_threshold=0.0)
    latest = engineered.sort_values("trading_date").tail(1)
    return latest[NUMERIC_FEATURES + CATEGORICAL_FEATURES + ["stock_id", "symbol", "trading_date"]].copy()


def _build_search_pipeline(model: Any) -> ImbPipeline:
    numeric_pipeline = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", RobustScaler()),
        ]
    )
    categorical_pipeline = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("encoder", OneHotEncoder(handle_unknown="ignore", sparse_output=False)),
        ]
    )
    preprocessor = ColumnTransformer(
        transformers=[
            ("num", numeric_pipeline, NUMERIC_FEATURES),
            ("cat", categorical_pipeline, CATEGORICAL_FEATURES),
        ],
        remainder="drop",
    )

    return ImbPipeline(
        steps=[
            ("preprocessor", preprocessor),
            ("feature_select", SelectFromModel(LogisticRegression(max_iter=600, solver="liblinear", penalty="l1", C=0.7))),
            ("dim_reduce", PCA(n_components=0.95, random_state=42)),
            ("oversample", RandomOverSampler(random_state=42)),
            ("undersample", RandomUnderSampler(random_state=42)),
            ("model", model),
        ]
    )


def train_and_select_model(
    frame: pd.DataFrame,
    horizon_days: int,
    positive_return_threshold: float,
) -> TrainedModelBundle:
    engineered = _engineer_features(frame, horizon_days=horizon_days, positive_return_threshold=positive_return_threshold)
    dataset = engineered.dropna(subset=NUMERIC_FEATURES + ["target"]).copy()
    dataset = dataset.sort_values(["market", "stock_id", "trading_date"]).reset_index(drop=True)

    if len(dataset) < 600:
        raise ValueError("Not enough clean rows for training. Need at least 600 rows after preprocessing.")
    if dataset["target"].nunique() < 2:
        raise ValueError("Training data has only one target class. Adjust threshold or widen training window.")

    dataset = dataset.sort_values("trading_date")
    split_index = int(len(dataset) * 0.8)
    train_df = dataset.iloc[:split_index]
    test_df = dataset.iloc[split_index:]

    x_train = train_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
    y_train = train_df["target"].astype(int)
    x_test = test_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
    y_test = test_df["target"].astype(int)

    model_specs: list[tuple[str, Any, dict[str, list[Any]]]] = [
        (
            "logistic_regression",
            LogisticRegression(max_iter=800, solver="liblinear"),
            {
                "model__C": [0.2, 0.5, 1.0, 2.0],
                "model__class_weight": [None, "balanced"],
            },
        ),
        (
            "random_forest",
            RandomForestClassifier(random_state=42),
            {
                "model__n_estimators": [120, 180, 250],
                "model__max_depth": [6, 10, None],
                "model__min_samples_leaf": [1, 3, 5],
                "model__class_weight": [None, "balanced"],
            },
        ),
        (
            "gradient_boosting",
            GradientBoostingClassifier(random_state=42),
            {
                "model__n_estimators": [120, 180, 250],
                "model__learning_rate": [0.03, 0.06, 0.1],
                "model__max_depth": [2, 3, 4],
                "model__subsample": [0.8, 1.0],
            },
        ),
        (
            "extra_trees",
            ExtraTreesClassifier(random_state=42),
            {
                "model__n_estimators": [120, 180, 250],
                "model__max_depth": [6, 10, None],
                "model__min_samples_leaf": [1, 3, 5],
                "model__class_weight": [None, "balanced"],
            },
        ),
        (
            "xgboost",
            XGBClassifier(
                objective="binary:logistic",
                eval_metric="logloss",
                random_state=42,
                n_jobs=1,
                tree_method="hist",
                verbosity=0,
            ),
            {
                "model__n_estimators": [120, 180, 250],
                "model__max_depth": [3, 5, 7],
                "model__learning_rate": [0.03, 0.06, 0.1],
                "model__subsample": [0.8, 1.0],
                "model__colsample_bytree": [0.8, 1.0],
            },
        ),
    ]

    splitter = TimeSeriesSplit(n_splits=2)
    evaluations: list[dict[str, float | str | Any]] = []

    for model_name, model, params in model_specs:
        pipeline = _build_search_pipeline(model)
        search = RandomizedSearchCV(
            estimator=pipeline,
            param_distributions=params,
            n_iter=4,
            cv=splitter,
            n_jobs=1,
            random_state=42,
            scoring="f1",
            refit=True,
        )
        search.fit(x_train, y_train)

        y_pred = search.best_estimator_.predict(x_test)
        result = {
            "model_name": model_name,
            "cv_f1": float(search.best_score_),
            "test_f1": float(f1_score(y_test, y_pred, zero_division=0)),
            "test_balanced_accuracy": float(balanced_accuracy_score(y_test, y_pred)),
            "test_precision": float(precision_score(y_test, y_pred, zero_division=0)),
            "test_recall": float(recall_score(y_test, y_pred, zero_division=0)),
            "estimator": search.best_estimator_,
        }
        evaluations.append(result)

    ranked = sorted(
        evaluations,
        key=lambda row: (float(row["test_f1"]), float(row["test_balanced_accuracy"]), float(row["cv_f1"])),
        reverse=True,
    )
    winner = ranked[0]

    model_version = datetime.now(timezone.utc).strftime("v%Y%m%d%H%M%S")
    return TrainedModelBundle(
        estimator=winner["estimator"],
        metrics=[
            {
                "model_name": str(item["model_name"]),
                "cv_f1": float(item["cv_f1"]),
                "test_f1": float(item["test_f1"]),
                "test_balanced_accuracy": float(item["test_balanced_accuracy"]),
                "test_precision": float(item["test_precision"]),
                "test_recall": float(item["test_recall"]),
            }
            for item in ranked
        ],
        selected_model=str(winner["model_name"]),
        model_version=model_version,
        horizon_days=horizon_days,
        positive_return_threshold=positive_return_threshold,
        trained_rows=len(dataset),
    )


def predict_action(
    estimator: Any,
    prediction_row: pd.DataFrame,
    horizon_days: int,
) -> dict[str, Any]:
    features = prediction_row[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
    proba = estimator.predict_proba(features)[0]

    # Class 1 = BUY, class 0 = SELL
    probability_sell = float(proba[0])
    probability_buy = float(proba[1])

    if probability_buy >= 0.55:
        action = "BUY"
        confidence = probability_buy
    elif probability_sell >= 0.55:
        action = "SELL"
        confidence = probability_sell
    else:
        action = "HOLD"
        confidence = max(probability_buy, probability_sell)

    price_vs_sma20 = float(prediction_row.iloc[0]["price_vs_sma20"]) if pd.notna(prediction_row.iloc[0]["price_vs_sma20"]) else None
    short_term_return = float(prediction_row.iloc[0]["return_5d"]) if pd.notna(prediction_row.iloc[0]["return_5d"]) else None

    reasoning: list[str] = [
        f"Model horizon: {horizon_days} trading days",
        f"BUY probability: {probability_buy:.2%}",
        f"SELL probability: {probability_sell:.2%}",
    ]
    if price_vs_sma20 is not None:
        reasoning.append(f"Price vs 20-day SMA: {price_vs_sma20:.2%}")
    if short_term_return is not None:
        reasoning.append(f"5-day return: {short_term_return:.2%}")

    return {
        "action": action,
        "confidence": confidence,
        "probability_buy": probability_buy,
        "probability_sell": probability_sell,
        "reasoning": reasoning,
    }


