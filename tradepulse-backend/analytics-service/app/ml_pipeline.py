from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

import numpy as np
import pandas as pd
from imblearn.pipeline import Pipeline as ImbPipeline
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingClassifier, RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score, precision_score, recall_score
from sklearn.model_selection import RandomizedSearchCV, TimeSeriesSplit
from sklearn.neighbors import KNeighborsClassifier
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.svm import SVC
from xgboost import XGBClassifier


NUMERIC_FEATURES = [
    "return_5d",
    "return_10d",
    "return_20d",
    "volatility_5d",
    "volatility_10d",
    "volatility_20d",
    "sma20_distance",
    "sma50_distance",
    "rsi",
    "macd",
    "volume_change",
]
CATEGORICAL_FEATURES: list[str] = []

ACTION_BUY = "BUY"
ACTION_SELL = "SELL"
ACTION_HOLD = "HOLD"
ACTION_THRESHOLD = 0.55


@dataclass
class TrainedModelBundle:
    estimator: Any
    metrics: list[dict[str, float | str]]
    selected_model: str
    model_version: str
    horizon_days: int
    decision_threshold: float
    trained_rows: int


def _engineer_features(
    frame: pd.DataFrame,
) -> pd.DataFrame:
    # Features and label are persisted in ml_weekly_features.
    data = frame.copy()
    data["trading_date"] = pd.to_datetime(data["trading_date"])
    data = data.sort_values(["stock_id", "trading_date"]).reset_index(drop=True)

    for col in NUMERIC_FEATURES:
        if col not in data.columns:
            data[col] = np.nan

    if "label" not in data.columns:
        data["label"] = np.nan

    label = pd.to_numeric(data["label"], errors="coerce")
    data["target"] = np.where(label == 1, 1.0, np.where(label == 0, 0.0, np.nan))

    return data.replace([np.inf, -np.inf], np.nan)


def build_prediction_row(frame: pd.DataFrame) -> pd.DataFrame:
    """Prepare a single prediction row from the latest stored data.

    All model features are pre-stored in the database so no rolling
    computation is needed — just normalise the frame and return it.
    """
    engineered = _engineer_features(frame)
    latest = engineered.sort_values("trading_date").tail(1)
    return latest[NUMERIC_FEATURES + CATEGORICAL_FEATURES + ["stock_id", "symbol", "trading_date"]].copy()


def _build_preprocessor(scale_numeric: bool) -> ColumnTransformer:
    numeric_steps: list[tuple[str, Any]] = [("imputer", SimpleImputer(strategy="median"))]
    if scale_numeric:
        numeric_steps.append(("scaler", StandardScaler()))

    numeric_pipeline = Pipeline(
        steps=[
            *numeric_steps,
        ]
    )
    transformers: list[tuple[str, Any, list[str]]] = [("num", numeric_pipeline, NUMERIC_FEATURES)]
    if CATEGORICAL_FEATURES:
        categorical_pipeline = Pipeline(
            steps=[
                ("imputer", SimpleImputer(strategy="most_frequent")),
                ("encoder", OneHotEncoder(handle_unknown="ignore", sparse_output=False)),
            ]
        )
        transformers.append(("cat", categorical_pipeline, CATEGORICAL_FEATURES))

    return ColumnTransformer(transformers=transformers, remainder="drop")


def _build_logistic_pipeline(model: Any) -> ImbPipeline:
    return ImbPipeline(
        steps=[
            ("preprocessor", _build_preprocessor(scale_numeric=True)),
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


def _max_search_iterations(param_grid: dict[str, list[Any]], requested_n_iter: int) -> int:
    total_combinations = 1
    for values in param_grid.values():
        total_combinations *= max(len(values), 1)
        if total_combinations >= requested_n_iter:
            return requested_n_iter
    return max(1, int(total_combinations))


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
    buy_clears_threshold = probability_buy >= decision_threshold
    sell_clears_threshold = probability_sell >= decision_threshold

    if buy_clears_threshold and sell_clears_threshold:
        if probability_buy > probability_sell:
            return ACTION_BUY, probability_buy
        if probability_sell > probability_buy:
            return ACTION_SELL, probability_sell
        return ACTION_HOLD, probability_buy

    if buy_clears_threshold:
        return ACTION_BUY, probability_buy
    if sell_clears_threshold:
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
    return _compute_trade_policy_metrics(
        probability_sell=probability_sell,
        probability_buy=probability_buy,
        y_true=y_test.to_numpy(dtype=int),
        decision_threshold=decision_threshold,
    )


def _compute_trade_policy_metrics(
    probability_sell: np.ndarray,
    probability_buy: np.ndarray,
    y_true: np.ndarray,
    decision_threshold: float,
) -> dict[str, float]:
    predicted_actions, _ = _derive_actions_from_probabilities(probability_sell, probability_buy, decision_threshold)
    actual_actions = np.where(y_true == 1, ACTION_BUY, ACTION_SELL)

    acted_mask = predicted_actions != ACTION_HOLD

    if not np.any(acted_mask):
        return {
            "test_f1": 0.0,
            "test_balanced_accuracy": 0.0,
            "test_precision": 0.0,
            "test_recall": 0.0,
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
    }


def _score_trade_policy(metrics: dict[str, float]) -> float:
    quality_score = (
        float(metrics["test_f1"]) * 0.6
        + float(metrics["test_balanced_accuracy"]) * 0.25
        + float(metrics["test_precision"]) * 0.15
    )
    return quality_score



def train_and_select_model(
    frame: pd.DataFrame,
    horizon_days: int,
) -> TrainedModelBundle:
    engineered = _engineer_features(frame)
    dataset = engineered.dropna(subset=NUMERIC_FEATURES + ["target"]).copy()
    dataset["target"] = dataset["target"].astype(int)
    dataset = dataset.sort_values(["market", "stock_id", "trading_date"]).reset_index(drop=True)

    if len(dataset) < 600:
        raise ValueError("Not enough clean rows for training. Need at least 600 rows after preprocessing.")
    if dataset["target"].nunique() < 2:
        raise ValueError("Training data has only one target class. Adjust threshold or widen training window.")

    class_counts = dataset["target"].value_counts()
    if int(class_counts.min()) < 150:
        raise ValueError(
            "Training labels are too imbalanced after neutral-band filtering. "
            "Increase days_back or reduce threshold/band."
        )

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
    _ = valid_df

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
                "model__C": [0.01, 0.05, 0.1, 0.5, 1.0, 2.0],
                "model__penalty": ["l1", "l2"],
                "model__solver": ["liblinear", "saga"],
                "model__class_weight": [None, "balanced"],
            },
        ),
        (
            "random_forest",
            RandomForestClassifier(random_state=42),
            {
                "model__n_estimators": [200, 400, 600],
                "model__max_depth": [3, 5, 8, 12, None],
                "model__min_samples_leaf": [2, 5, 10],
                "model__max_features": ["sqrt", 0.7],
            },
        ),
        (
            "gradient_boosting",
            GradientBoostingClassifier(random_state=42),
            {
                "model__n_estimators": [200, 400],
                "model__learning_rate": [0.01, 0.03, 0.05],
                "model__max_depth": [2, 3, 4],
                "model__subsample": [0.7, 0.85, 1.0],
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
                "model__n_estimators": [300, 500],
                "model__max_depth": [2, 3, 4, 5, 6],
                "model__learning_rate": [0.01, 0.03, 0.05],
                "model__min_child_weight": [3, 5, 10],
                "model__subsample": [0.7, 0.85],
                "model__colsample_bytree": [0.7, 0.85],
                "model__reg_lambda": [1.0, 5.0, 10.0],
                "model__reg_alpha": [0.0, 0.1, 1.0],
                "model__scale_pos_weight": [xgb_scale_pos_weight],
            },
        ),
        (
            "knn",
            KNeighborsClassifier(),
            {
                "model__n_neighbors": [5, 9, 15, 25],
                "model__weights": ["uniform", "distance"],
                "model__p": [1, 2],
                "model__leaf_size": [20, 30, 40],
            },
        ),
        (
            "svm",
            SVC(probability=True, random_state=42),
            {
                "model__C": [0.1, 0.5, 1.0, 2.0, 5.0],
                "model__kernel": ["linear", "rbf"],
                "model__gamma": ["scale", "auto", 0.01, 0.1],
                "model__class_weight": [None, "balanced"],
            },
        ),
    ]

    splitter = TimeSeriesSplit(n_splits=3)
    evaluations: list[dict[str, float | str | Any]] = []

    for model_name, model, params in model_specs:
        pipeline = (
            _build_logistic_pipeline(model)
            if model_name in {"logistic_regression", "knn", "svm"}
            else _build_tree_pipeline(model)
        )
        search = RandomizedSearchCV(
            estimator=pipeline,
            param_distributions=params,
            n_iter=_max_search_iterations(params, requested_n_iter=8),
            cv=splitter,
            n_jobs=1,
            random_state=42,
            scoring="balanced_accuracy",
            refit=True,
        )
        search.fit(x_train_core, y_train_core)

        tuned_threshold = ACTION_THRESHOLD
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
            "policy_score": float(_score_trade_policy(policy_metrics)),
            "decision_threshold": float(tuned_threshold),
            "estimator": fitted_estimator,
        }
        evaluations.append(result)

    ranked = sorted(
        evaluations,
        key=lambda row: (
            float(row["policy_score"]),
            float(row["test_balanced_accuracy"]),
            float(row["test_f1"]),
            float(row["test_precision"]),
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
            }
            for item in ranked
        ],
        selected_model=str(winner["model_name"]),
        model_version=model_version,
        horizon_days=horizon_days,
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

    return_5d = float(prediction_row.iloc[0]["return_5d"]) if pd.notna(prediction_row.iloc[0]["return_5d"]) else None
    volatility_20d = float(prediction_row.iloc[0]["volatility_20d"]) if pd.notna(prediction_row.iloc[0]["volatility_20d"]) else None

    reasoning: list[str] = [
        f"Model horizon: {horizon_days} trading days",
        f"BUY probability: {probability_buy:.2%}",
        f"SELL probability: {probability_sell:.2%}",
    ]
    if return_5d is not None:
        reasoning.append(f"Current 5-day return: {return_5d:.2f}%")
    if volatility_20d is not None:
        reasoning.append(f"Current 20-day volatility: {volatility_20d:.2f}%")

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

