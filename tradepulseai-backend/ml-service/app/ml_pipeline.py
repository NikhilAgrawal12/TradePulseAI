from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

import numpy as np
import pandas as pd
from imblearn.pipeline import Pipeline as ImbPipeline
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import ExtraTreesClassifier, GradientBoostingClassifier, RandomForestClassifier
from sklearn.feature_selection import SelectFromModel
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score, precision_score, recall_score
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

ACTION_BUY = "BUY"
ACTION_SELL = "SELL"
ACTION_HOLD = "HOLD"
ACTION_THRESHOLD = 0.55
MIN_ACTION_RATE = 0.35
MAX_ACTION_RATE = 0.9
TARGET_ACTION_RATE = 0.65


@dataclass
class TrainedModelBundle:
    estimator: Any
    metrics: list[dict[str, float | str]]
    selected_model: str
    model_version: str
    horizon_days: int
    positive_return_threshold: float
    decision_threshold: float
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


def _build_preprocessor(scale_numeric: bool) -> ColumnTransformer:
    numeric_steps: list[tuple[str, Any]] = [("imputer", SimpleImputer(strategy="median"))]
    if scale_numeric:
        numeric_steps.append(("scaler", RobustScaler()))

    numeric_pipeline = Pipeline(
        steps=[
            *numeric_steps,
        ]
    )
    categorical_pipeline = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("encoder", OneHotEncoder(handle_unknown="ignore", sparse_output=False)),
        ]
    )
    return ColumnTransformer(
        transformers=[
            ("num", numeric_pipeline, NUMERIC_FEATURES),
            ("cat", categorical_pipeline, CATEGORICAL_FEATURES),
        ],
        remainder="drop",
    )


def _build_logistic_pipeline(model: Any) -> ImbPipeline:
    return ImbPipeline(
        steps=[
            ("preprocessor", _build_preprocessor(scale_numeric=True)),
            ("feature_select", SelectFromModel(LogisticRegression(max_iter=600, solver="liblinear", penalty="l1", C=0.7))),
            ("model", model),
        ]
    )


def _build_tree_pipeline(model: Any) -> ImbPipeline:
    return ImbPipeline(
        steps=[
            ("preprocessor", _build_preprocessor(scale_numeric=False)),
            ("model", model),
        ]
    )


def _get_probability_columns(estimator: Any, probabilities: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    classes = getattr(estimator, "classes_", None)
    if classes is None and hasattr(estimator, "named_steps"):
        classes = getattr(estimator.named_steps.get("model"), "classes_", None)

    if classes is None:
        raise ValueError("Trained estimator does not expose probability classes.")

    class_list = [int(label) for label in classes]
    if 0 not in class_list or 1 not in class_list:
        raise ValueError(f"Expected binary classes [0, 1], found {class_list!r}.")

    sell_index = class_list.index(0)
    buy_index = class_list.index(1)
    return probabilities[:, sell_index].astype(float), probabilities[:, buy_index].astype(float)


def _derive_action(probability_sell: float, probability_buy: float, decision_threshold: float) -> tuple[str, float]:
    if probability_buy >= decision_threshold:
        return ACTION_BUY, probability_buy
    if probability_sell >= decision_threshold:
        return ACTION_SELL, probability_sell
    return ACTION_HOLD, max(probability_buy, probability_sell)


def _derive_actions_from_probabilities(
    probability_sell: np.ndarray,
    probability_buy: np.ndarray,
    decision_threshold: float,
) -> tuple[np.ndarray, np.ndarray]:
    actions: list[str] = []
    confidences: list[float] = []
    for sell_value, buy_value in zip(probability_sell, probability_buy, strict=True):
        action, confidence = _derive_action(float(sell_value), float(buy_value), decision_threshold)
        actions.append(action)
        confidences.append(confidence)
    return np.asarray(actions, dtype=object), np.asarray(confidences, dtype=float)


def _split_train_test_by_date(dataset: pd.DataFrame, train_fraction: float = 0.8) -> tuple[pd.DataFrame, pd.DataFrame]:
    unique_dates = np.sort(dataset["trading_date"].dropna().unique())
    if len(unique_dates) < 10:
        raise ValueError("Not enough unique trading dates for temporal split. Need at least 10 dates.")

    split_index = min(max(int(len(unique_dates) * train_fraction), 1), len(unique_dates) - 1)
    train_dates = set(unique_dates[:split_index])
    test_dates = set(unique_dates[split_index:])

    train_df = dataset[dataset["trading_date"].isin(train_dates)].copy()
    test_df = dataset[dataset["trading_date"].isin(test_dates)].copy()

    if train_df.empty or test_df.empty:
        raise ValueError("Temporal split produced an empty train or test set. Widen the training window.")

    return train_df, test_df


def _evaluate_trade_policy(
    estimator: Any,
    x_test: pd.DataFrame,
    y_test: pd.Series,
    decision_threshold: float,
) -> dict[str, float]:
    probabilities = estimator.predict_proba(x_test)
    probability_sell, probability_buy = _get_probability_columns(estimator, probabilities)
    predicted_actions, _ = _derive_actions_from_probabilities(probability_sell, probability_buy, decision_threshold)
    actual_actions = np.where(y_test.to_numpy(dtype=int) == 1, ACTION_BUY, ACTION_SELL)

    acted_mask = predicted_actions != ACTION_HOLD
    action_rate = float(np.mean(acted_mask))
    hold_rate = float(1.0 - action_rate)

    if not np.any(acted_mask):
        return {
            "test_f1": 0.0,
            "test_balanced_accuracy": 0.0,
            "test_precision": 0.0,
            "test_recall": 0.0,
            "test_action_rate": action_rate,
            "test_hold_rate": hold_rate,
        }

    y_true_trade = actual_actions[acted_mask]
    y_pred_trade = predicted_actions[acted_mask]
    labels = [ACTION_SELL, ACTION_BUY]

    macro_recall = float(recall_score(y_true_trade, y_pred_trade, average="macro", labels=labels, zero_division=0))
    return {
        "test_f1": float(f1_score(y_true_trade, y_pred_trade, average="macro", labels=labels, zero_division=0)),
        "test_balanced_accuracy": macro_recall,
        "test_precision": float(precision_score(y_true_trade, y_pred_trade, average="macro", labels=labels, zero_division=0)),
        "test_recall": macro_recall,
        "test_action_rate": action_rate,
        "test_hold_rate": hold_rate,
    }


def _score_trade_policy(metrics: dict[str, float]) -> float:
    action_rate = float(metrics["test_action_rate"])
    if action_rate < MIN_ACTION_RATE:
        coverage_penalty = 0.8 * (action_rate / max(MIN_ACTION_RATE, 1e-9))
    elif action_rate > MAX_ACTION_RATE:
        coverage_penalty = 0.85
    else:
        coverage_penalty = 1.0 - (abs(action_rate - TARGET_ACTION_RATE) / max(TARGET_ACTION_RATE, 1e-9)) * 0.1

    quality_score = (
        float(metrics["test_f1"]) * 0.55
        + float(metrics["test_precision"]) * 0.25
        + float(metrics["test_balanced_accuracy"]) * 0.2
    )
    return quality_score * coverage_penalty


def _build_threshold_candidates(y_valid: pd.Series) -> list[float]:
    positive_rate = float(y_valid.mean()) if len(y_valid) else 0.5
    lower_bound = max(0.52, min(ACTION_THRESHOLD, positive_rate + 0.01))
    upper_bound = min(0.75, max(lower_bound + 0.1, positive_rate + 0.2))
    grid = np.linspace(lower_bound, upper_bound, num=7)
    candidates = {float(round(value, 3)) for value in grid}
    candidates.add(ACTION_THRESHOLD)
    return sorted(candidates)


def _tune_decision_threshold(estimator: Any, x_valid: pd.DataFrame, y_valid: pd.Series) -> tuple[float, dict[str, float]]:
    best_threshold = ACTION_THRESHOLD
    best_metrics = _evaluate_trade_policy(estimator, x_valid, y_valid, ACTION_THRESHOLD)
    best_score = _score_trade_policy(best_metrics)

    for threshold in _build_threshold_candidates(y_valid):
        metrics = _evaluate_trade_policy(estimator, x_valid, y_valid, float(threshold))
        score = _score_trade_policy(metrics)
        if score > best_score:
            best_threshold = float(threshold)
            best_metrics = metrics
            best_score = score

    return best_threshold, best_metrics


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

    dataset = dataset.sort_values(["trading_date", "stock_id", "market"]).reset_index(drop=True)
    train_df, test_df = _split_train_test_by_date(dataset)

    x_train = train_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
    y_train = train_df["target"].astype(int)
    x_test = test_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
    y_test = test_df["target"].astype(int)

    if y_train.nunique() < 2:
        raise ValueError("Training split has only one target class. Adjust threshold or widen training window.")

    train_core_df, valid_df = _split_train_test_by_date(train_df, train_fraction=0.85)
    x_train_core = train_core_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
    y_train_core = train_core_df["target"].astype(int)
    x_valid = valid_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
    y_valid = valid_df["target"].astype(int)

    if y_train_core.nunique() < 2:
        raise ValueError("Validation split produced one training class. Widen training window or lower threshold.")

    negative_count = int((y_train_core == 0).sum())
    positive_count = int((y_train_core == 1).sum())
    imbalance_ratio = (negative_count / max(positive_count, 1)) if positive_count > 0 else 1.0
    xgb_scale_pos_weight = max(1.0, min(8.0, float(imbalance_ratio)))

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
                scale_pos_weight=xgb_scale_pos_weight,
            ),
            {
                "model__n_estimators": [120, 180, 250],
                "model__max_depth": [3, 5, 7],
                "model__learning_rate": [0.03, 0.06, 0.1],
                "model__subsample": [0.8, 1.0],
                "model__colsample_bytree": [0.8, 1.0],
                "model__scale_pos_weight": [xgb_scale_pos_weight],
            },
        ),
    ]

    splitter = TimeSeriesSplit(n_splits=2)
    evaluations: list[dict[str, float | str | Any]] = []

    for model_name, model, params in model_specs:
        pipeline = _build_logistic_pipeline(model) if model_name == "logistic_regression" else _build_tree_pipeline(model)
        search = RandomizedSearchCV(
            estimator=pipeline,
            param_distributions=params,
            n_iter=6,
            cv=splitter,
            n_jobs=1,
            random_state=42,
            scoring="f1",
            refit=True,
        )
        search.fit(x_train_core, y_train_core)

        tuned_threshold, _ = _tune_decision_threshold(search.best_estimator_, x_valid, y_valid)
        fitted_estimator = deepcopy(search.best_estimator_)
        fitted_estimator.fit(x_train, y_train)
        policy_metrics = _evaluate_trade_policy(fitted_estimator, x_test, y_test, tuned_threshold)
        result = {
            "model_name": model_name,
            "cv_f1": float(search.best_score_),
            "test_f1": float(policy_metrics["test_f1"]),
            "test_balanced_accuracy": float(policy_metrics["test_balanced_accuracy"]),
            "test_precision": float(policy_metrics["test_precision"]),
            "test_recall": float(policy_metrics["test_recall"]),
            "test_action_rate": float(policy_metrics["test_action_rate"]),
            "test_hold_rate": float(policy_metrics["test_hold_rate"]),
            "policy_score": float(_score_trade_policy(policy_metrics)),
            "decision_threshold": float(tuned_threshold),
            "estimator": fitted_estimator,
        }
        evaluations.append(result)

    ranked = sorted(
        evaluations,
        key=lambda row: (
            float(row["policy_score"]),
            float(row["test_precision"]),
            float(row["test_balanced_accuracy"]),
            float(row["cv_f1"]),
        ),
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
                "test_action_rate": float(item["test_action_rate"]),
                "test_hold_rate": float(item["test_hold_rate"]),
            }
            for item in ranked
        ],
        selected_model=str(winner["model_name"]),
        model_version=model_version,
        horizon_days=horizon_days,
        positive_return_threshold=positive_return_threshold,
        decision_threshold=float(winner["decision_threshold"]),
        trained_rows=len(dataset),
    )


def predict_action(
    estimator: Any,
    prediction_row: pd.DataFrame,
    horizon_days: int,
    decision_threshold: float = ACTION_THRESHOLD,
) -> dict[str, Any]:
    features = prediction_row[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
    probabilities = estimator.predict_proba(features)
    probability_sell_values, probability_buy_values = _get_probability_columns(estimator, probabilities)
    probability_sell = float(probability_sell_values[0])
    probability_buy = float(probability_buy_values[0])
    action, confidence = _derive_action(probability_sell, probability_buy, decision_threshold)
    confidence_edge = max(0.0, confidence - decision_threshold)
    probability_gap = abs(probability_buy - probability_sell)

    if confidence_edge >= 0.12:
        conviction_label = "HIGH"
    elif confidence_edge >= 0.05:
        conviction_label = "MEDIUM"
    elif confidence_edge > 0:
        conviction_label = "LOW"
    else:
        conviction_label = "NEUTRAL"

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
        "confidence_edge": confidence_edge,
        "probability_gap": probability_gap,
        "conviction_label": conviction_label,
        "reasoning": reasoning,
    }


