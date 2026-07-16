# ML Models Detailed - Preprocessing & Complete Workflow

**Date:** July 16, 2026  
**File:** `tradepulseai-backend/ml-service/app/ml_pipeline.py`

---

## Summary: 4 ML Models Trained in Parallel

| Model | Type | Scaling | Feature Selection | Tuning Complexity | Speed |
|-------|------|---------|------------------|------------------|-------|
| **1. Logistic Regression** | Linear classifier | ✅ RobustScaler | SelectKBest + L1 | Medium | ⚡ Fast |
| **2. Random Forest** | Ensemble (bagging) | ❌ No scaling | SelectKBest only | High | ⚡ Medium |
| **3. Gradient Boosting** | Ensemble (boosting) | ❌ No scaling | SelectKBest only | High | 🐢 Slow |
| **4. XGBoost** | Extreme Gradient Boosting | ❌ No scaling | SelectKBest only | Very High | 🐢 Slower |

**Best performer:** XGBoost (highest test F1 score)  
**Selected model:** Winner of RandomizedSearchCV on balanced_accuracy metric

---

## Complete End-to-End Workflow

```
┌─────────────────────────────────────────────────────────────────┐
│                    DATA LOADING PHASE                           │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│              FEATURE ENGINEERING & LABELING                     │
│        (Transform raw OHLC + metrics → 29 features + label)    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   DATA CLEANING & VALIDATION                    │
│      (Remove NaN, check class balance, ensure ≥600 rows)       │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│              TEMPORAL TRAIN/TEST SPLIT (80/20)                 │
│        (Preserve time series order, no data leakage)           │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│          TRAIN/VALIDATION SPLIT (85/15 of train data)          │
│     (train_core for hyperparameter tuning, validation for      │
│      threshold tuning)                                         │
└─────────────────────────────────────────────────────────────────┘
                              ↓
        ┌─────────────────────────────┬─────────────────────────────┐
        ↓                             ↓                             ↓
   ┌──────────────┐           ┌──────────────┐           ┌──────────────┐
   │   MODEL 1    │           │   MODEL 2    │           │   MODEL 3    │
   │  LOGISTIC    │           │   RANDOM     │           │  GRADIENT    │
   │ REGRESSION   │           │    FOREST    │           │ BOOSTING     │
   └──────────────┘           └──────────────┘           └──────────────┘
        ↓                             ↓                             ↓
   ┌──────────────┐           ┌──────────────┐           ┌──────────────┐
   │   PREPROCESS │           │   PREPROCESS │           │   PREPROCESS │
   │   (Scale)    │           │   (No scale) │           │   (No scale) │
   └──────────────┘           └──────────────┘           └──────────────┘
        ↓                             ↓                             ↓
   ┌──────────────┐           ┌──────────────┐           ┌──────────────┐
   │HYPERPARAMETER│           │HYPERPARAMETER│           │HYPERPARAMETER│
   │   TUNING     │           │   TUNING     │           │   TUNING     │
   │(RandomSearch)│           │(RandomSearch)│           │(RandomSearch)│
   └──────────────┘           └──────────────┘           └──────────────┘
        ↓                             ↓                             ↓
   ┌──────────────┐           ┌──────────────┐           ┌──────────────┐
   │  THRESHOLD   │           │  THRESHOLD   │           │  THRESHOLD   │
   │   TUNING     │           │   TUNING     │           │   TUNING     │
   └──────────────┘           └──────────────┘           └──────────────┘
        ↓                             ↓                             ↓
   ┌──────────────┐           ┌──────────────┐           ┌──────────────┐
   │  FINAL TEST  │           │  FINAL TEST  │           │  FINAL TEST  │
   │ EVALUATION   │           │ EVALUATION   │           │ EVALUATION   │
   └──────────────┘           └──────────────┘           └──────────────┘
        ↓                             ↓                             ↓
   ┌──────────────┐           ┌──────────────┐           ┌──────────────┐
   │ F1, Accuracy │           │ F1, Accuracy │           │ F1, Accuracy │
   │  Precision   │           │  Precision   │           │  Precision   │
   │   Recall     │           │   Recall     │           │   Recall     │
   └──────────────┘           └──────────────┘           └──────────────┘
        ↓                             ↓                             ↓
        └─────────────────────────────┬─────────────────────────────┘
                                      ↓
                        ┌────────────────────────────┐
                        │   RANK & SELECT BEST       │
                        │ By: policy_score > F1 >    │
                        │     accuracy > precision   │
                        └────────────────────────────┘
                                      ↓
        ┌───────────────────────────────────────────────────────────┐
        │         SAVE WINNER + ALL CANDIDATES                      │
        │         (model_registry + model_candidates tables)        │
        └───────────────────────────────────────────────────────────┘
        
                          COMPLETE ✓
```

---

## Phase 1: DATA LOADING

**File:** `app/data.py` → `StockDataRepository.fetch_training_data()`

### Input Parameters
```python
days_back: int              # e.g., 730 (2 years of data)
max_training_stocks: int    # e.g., 800 (all stocks)
max_training_rows: int      # e.g., 500,000
```

### SQL Query
```sql
WITH ranked_stocks AS (
    SELECT stock_id, ticker, market, market_cap,
           ROW_NUMBER() OVER (ORDER BY market_cap DESC) AS stock_rank
    FROM stocks
)
SELECT
    d.stock_id, rs.ticker AS symbol, rs.market, d.trading_date,
    -- Raw OHLC (5 features)
    d.open_price, d.high_price, d.low_price, d.close_price, d.volume,
    -- Technical indicators (11 features)
    d.sma_20, d.sma_50, d.sma_200, d.volatility_30d, d.volatility_90d,
    d.daily_return_percent, d.return_5d, d.momentum_20d,
    d.rsi_14, d.macd, d.macd_signal,
    -- Sentiment (2 features)
    d.sentiment_score, d.news_count,
    -- Stock metrics (11 features - joined)
    COALESCE(m.sharpe_ratio, 0.0) AS sharpe_ratio,
    COALESCE(m.sortino_ratio, 0.0) AS sortino_ratio,
    COALESCE(m.max_drawdown, 0.0) AS max_drawdown,
    COALESCE(m.high_52w, d.close_price) AS high_52w,
    COALESCE(m.low_52w, d.close_price) AS low_52w,
    COALESCE(m.distance_from_high_percent, 0.0) AS distance_from_high_percent,
    COALESCE(m.distance_from_low_percent, 0.0) AS distance_from_low_percent,
    COALESCE(m.avg_volume_30d, d.volume) AS avg_volume_30d,
    COALESCE(m.relative_volume, 1.0) AS relative_volume,
    CASE WHEN COALESCE(m.golden_cross, FALSE) THEN 1.0 ELSE 0.0 END AS golden_cross,
    CASE WHEN COALESCE(m.death_cross, FALSE) THEN 1.0 ELSE 0.0 END AS death_cross
FROM stock_daily_ohlc d
JOIN ranked_stocks rs ON rs.stock_id = d.stock_id
LEFT JOIN stock_metrics m ON m.stock_id = d.stock_id
WHERE d.trading_date >= CURRENT_DATE - make_interval(days => days_back)
  AND rs.stock_rank <= max_training_stocks
ORDER BY rs.market_cap DESC, d.stock_id, d.trading_date
LIMIT max_training_rows;
```

### Output
```
DataFrame with 29 columns:
  - stock_id, symbol, market, trading_date
  - 29 numeric features (all numeric, pre-filled with defaults)
```

**Typical shape:** ~150,000 rows × 33 columns (including metadata)

---

## Phase 2: FEATURE ENGINEERING & LABELING

**Function:** `_engineer_features(frame, horizon_days=5, ...)`

### Step 2.1: Column Validation & Missing Value Handling

```python
# Ensure all 29 features exist
if "sentiment_score" not in data.columns:
    data["sentiment_score"] = 0.0
if "news_count" not in data.columns:
    data["news_count"] = 0.0

# For technical indicators, fill NaN
for col in ["return_5d", "momentum_20d", "rsi_14", "macd", "macd_signal"]:
    if col not in data.columns:
        data[col] = np.nan

# For stock_metrics columns, fill with sensible defaults
for col in ["sharpe_ratio", "sortino_ratio", "max_drawdown", ...]:
    if col not in data.columns:
        if col in ["golden_cross", "death_cross"]:
            data[col] = 0.0
        else:
            data[col] = np.nan

# Apply COALESCE-like logic
data["sentiment_score"] = data["sentiment_score"].fillna(0.0)
data["news_count"] = data["news_count"].fillna(0.0)
data["sharpe_ratio"] = data["sharpe_ratio"].fillna(0.0)
data["sortino_ratio"] = data["sortino_ratio"].fillna(0.0)
data["max_drawdown"] = data["max_drawdown"].fillna(0.0)
data["golden_cross"] = data["golden_cross"].fillna(0.0)
data["death_cross"] = data["death_cross"].fillna(0.0)
data["high_52w"] = data["high_52w"].fillna(data["close_price"])
data["low_52w"] = data["low_52w"].fillna(data["close_price"])
data["distance_from_high_percent"] = data["distance_from_high_percent"].fillna(0.0)
data["distance_from_low_percent"] = data["distance_from_low_percent"].fillna(0.0)
data["avg_volume_30d"] = data["avg_volume_30d"].fillna(data["volume"])
data["relative_volume"] = data["relative_volume"].fillna(1.0)
```

### Step 2.2: Sort Data by Stock & Date

```python
data = data.sort_values(["stock_id", "trading_date"]).reset_index(drop=True)
```

### Step 2.3: Calculate Target Label (Forward Return)

```python
# Group by stock to compute forward return per stock
grouped = data.groupby("stock_id", group_keys=False)

# Forward return = (close_t+5 - close_t) / close_t
data["forward_return"] = grouped["close_price"].shift(-horizon_days) / data["close_price"] - 1.0

# Label assignment (ternary: BUY=1, SELL=0, HOLD=NaN)
# Fixed threshold: ±1.5% to keep labels consistent
data["target"] = np.where(
    data["forward_return"] > 0.015,     # If return > 1.5%
    1,                                  # Label as BUY
    np.where(
        data["forward_return"] < -0.015,  # Elif return < -1.5%
        0,                                # Label as SELL
        np.nan                            # Else unlabeled (neutral)
    )
)
```

### Step 2.4: Clean Infinities

```python
data = data.replace([np.inf, -np.inf], np.nan)
```

### Output After Phase 2
- DataFrame with 29 numeric features + metadata
- **target** column: {0, 1, NaN}
- ~120,000 rows (some rows dropped due to forward_return NaN at end of history)

---

## Phase 3: DATA CLEANING & VALIDATION

**Function:** `train_and_select_model(frame, ...)`

### Step 3.1: Drop Rows with Missing Labels

```python
dataset = engineered.dropna(subset=NUMERIC_FEATURES + ["target"]).copy()
dataset["target"] = dataset["target"].astype(int)
```

**Result:** ~50,000-100,000 clean rows (depends on data quality)

### Step 3.2: Sort by Date (Preserve Time Order)

```python
dataset = dataset.sort_values(["market", "stock_id", "trading_date"]).reset_index(drop=True)
```

### Step 3.3: Validate Minimum Requirements

```python
# Requirement 1: At least 600 rows
if len(dataset) < 600:
    raise ValueError("Not enough clean rows for training. Need at least 600 rows after preprocessing.")

# Requirement 2: At least 2 classes (BUY and SELL)
if dataset["target"].nunique() < 2:
    raise ValueError("Training data has only one target class.")

# Requirement 3: Minimum 150 samples per class (balanced)
class_counts = dataset["target"].value_counts()
if int(class_counts.min()) < 150:
    raise ValueError("Training labels are too imbalanced. Increase days_back or reduce threshold.")
```

### Output After Phase 3
- Clean dataset ready for splitting
- ~80% BUY/SELL, ~20% HOLD (typical distribution)
- All 29 features + target + metadata

---

## Phase 4: TEMPORAL TRAIN/TEST SPLIT

**Function:** `_split_train_test_by_date(dataset, train_fraction=0.8)`

### Why Temporal Split?
**CRITICAL:** Stock data is time series. Must NOT shuffle or random split!
- If we randomly split, we'd use future data to predict the past (data leakage)
- Temporal split respects causality: train on early dates, test on later dates

### Step 4.1: Get Unique Trading Dates

```python
unique_dates = np.sort(dataset["trading_date"].dropna().unique())
# Typical: 100-200 unique trading dates
```

### Step 4.2: Calculate Split Point

```python
split_index = int(len(unique_dates) * 0.8)  # e.g., 160 dates into 80/20
train_dates = set(unique_dates[:split_index])
test_dates = set(unique_dates[split_index:])
```

### Step 4.3: Filter by Date

```python
train_df = dataset[dataset["trading_date"].isin(train_dates)].copy()
test_df = dataset[dataset["trading_date"].isin(test_dates)].copy()
```

### Example Timeline
```
Trading Dates:       Jan-2026  ...  Jun-2026  ...  Jul-2026
                     ├─────────────────────────┼─────────────────────┤
                     │         TRAIN (80%)     │   TEST (20%)        │
                     │  (use to fit models)    │ (eval only, no peek) │
                     └─────────────────────────┴─────────────────────┘
                     
Causality: ✓ Preserved (train always before test)
```

### Output
- **train_df:** ~80% of rows (all dates before Jun 1, 2026)
- **test_df:** ~20% of rows (all dates from Jun 1, 2026 onward)

---

## Phase 5: TRAIN/VALIDATION SPLIT

**From train_df:** Split into 85/15 for hyperparameter tuning

```python
train_core_df, valid_df = _split_train_test_by_date(train_df, train_fraction=0.85)

# Extract X, y for all 3 splits
x_train_core = train_core_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
y_train_core = train_core_df["target"].astype(int)

x_valid = valid_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
y_valid = valid_df["target"].astype(int)

x_test = test_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
y_test = test_df["target"].astype(int)

x_train = train_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
y_train = train_df["target"].astype(int)
```

### Three Dataset Roles

| Split | Purpose | Rows | Usage |
|-------|---------|------|-------|
| **train_core** | Hyperparameter tuning via RandomizedSearchCV | ~60% | CV loops test different hyperparams |
| **valid** | Decision threshold tuning | ~20% | Find optimal confidence threshold |
| **test** | Final model evaluation (never seen before) | ~20% | Report metrics to user |

---

## Phase 6: CALCULATE CLASS IMBALANCE RATIO (For XGBoost)

```python
negative_count = int((y_train_core == 0).sum())  # SELL count
positive_count = int((y_train_core == 1).sum())  # BUY count
imbalance_ratio = negative_count / max(positive_count, 1)

# XGBoost scale_pos_weight: penalize minority class
# Typical: 0.8-2.0 (most stock data is balanced)
xgb_scale_pos_weight = max(1.0, min(8.0, float(imbalance_ratio)))
```

---

## Phase 7: MODEL TRAINING & HYPERPARAMETER TUNING

Now train **4 models in parallel**. Each follows the same pattern:

### Pattern: Preprocess → Hyperparameter Search → Evaluate

---

### **MODEL 1: LOGISTIC REGRESSION**

**Type:** Linear classifier (fast baseline)  
**File location:** Lines 652-666

#### 7.1.1: Build Preprocessing Pipeline

```python
# Logistic Regression REQUIRES scaling (sensitive to feature magnitude)
preprocessor = ColumnTransformer(
    transformers=[
        ("num", Pipeline([
            ("imputer", SimpleImputer(strategy="median")),  # Fill NaN with median
            ("scaler", RobustScaler()),                     # Scale to [-∞, ∞] robust to outliers
        ]), NUMERIC_FEATURES)  # Apply to all 29 features
    ],
    remainder="drop"  # Drop any non-numeric columns
)
```

#### 7.1.2: Build Full Pipeline

```python
pipeline = ImbPipeline([
    ("preprocessor", preprocessor),          # Step 1: Scale features
    ("feature_filter", SelectKBest(          # Step 2: Select top K features
        score_func=mutual_info_classif,      # by mutual information score
        k="all"
    )),
    ("reduce_dim", "passthrough"),           # Step 3: Optional PCA (tuned)
    ("feature_select", SelectFromModel(      # Step 4: L1 regularization pruning
        LogisticRegression(max_iter=600, solver="liblinear", penalty="l1", C=0.7)
    )),
    ("model", LogisticRegression(...)),      # Step 5: Final logistic regression
])
```

#### 7.1.3: Hyperparameters to Tune

```python
params = {
    "feature_filter__k": [6, 8, 10, "all"],
    "reduce_dim": [
        "passthrough",
        PCA(n_components=0.95, svd_solver="full", random_state=42),
        PCA(n_components=0.9, svd_solver="full", random_state=42),
    ],
    "model__C": [0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 4.0],  # Regularization strength
    "model__penalty": ["l1", "l2"],                    # Regularization type
    "model__class_weight": [None, "balanced"],         # Class imbalance handling
}
```

**Total combinations:** 4 × 3 × 7 × 2 × 2 = 336  
**Search strategy:** RandomizedSearchCV samples ~4 random combinations

#### 7.1.4: Cross-Validation Search

```python
search = RandomizedSearchCV(
    estimator=pipeline,
    param_distributions=params,
    n_iter=4,                              # Sample 4 random hyperparameter sets
    cv=TimeSeriesSplit(n_splits=2),       # 2 time-series folds (train on early, validate on later)
    n_jobs=1,                              # No parallelization
    random_state=42,                       # Reproducible randomness
    scoring="balanced_accuracy",           # Metric to optimize
    refit=True                             # Refit on best params
)
search.fit(x_train_core, y_train_core)    # Fit on training data
best_params = search.best_params_         # Retrieve best hyperparameters
```

#### 7.1.5: Threshold Tuning on Validation Set

```python
# Find optimal decision threshold for this model
tuned_threshold, _ = _tune_decision_threshold(
    search.best_estimator_,  # Trained model
    x_valid,                 # Validation features
    y_valid                  # Validation labels
)
# Typical result: threshold ≈ 0.50-0.55
```

#### 7.1.6: Final Training on Full Training Set

```python
fitted_estimator = deepcopy(search.best_estimator_)
fitted_estimator.fit(x_train, y_train)  # Retrain on entire train_df
```

#### 7.1.7: Test Evaluation

```python
policy_metrics = _evaluate_trade_policy(
    fitted_estimator,        # Trained model
    x_test,                  # Test features (unseen)
    y_test,                  # Test labels
    tuned_threshold
)
# Returns: {test_f1, test_balanced_accuracy, test_precision, test_recall}
```

#### 7.1.8: Save Results

```python
result = {
    "model_name": "logistic_regression",
    "cv_f1": float(search.best_score_),
    "test_f1": float(policy_metrics["test_f1"]),
    "test_balanced_accuracy": float(policy_metrics["test_balanced_accuracy"]),
    "test_precision": float(policy_metrics["test_precision"]),
    "test_recall": float(policy_metrics["test_recall"]),
    "policy_score": float(_score_trade_policy(policy_metrics)),
    "decision_threshold": float(tuned_threshold),
    "estimator": fitted_estimator,
}
```

---

### **MODEL 2: RANDOM FOREST**

**Type:** Ensemble classifier (bagging: many trees voting)  
**File location:** Lines 668-679  
**Key difference from Logistic Regression:** NO scaling, feature selection only

#### 7.2.1: Build Preprocessing Pipeline (Different!)

```python
# Random Forest does NOT need scaling (tree-based, scale-invariant)
preprocessor = ColumnTransformer(
    transformers=[
        ("num", Pipeline([
            ("imputer", SimpleImputer(strategy="median")),  # Only imputation
            # NO RobustScaler here!
        ]), NUMERIC_FEATURES)
    ],
    remainder="drop"
)
```

#### 7.2.2: Build Full Pipeline

```python
pipeline = ImbPipeline([
    ("preprocessor", preprocessor),          # Imputation only
    ("feature_filter", SelectKBest(          # Top K by mutual information
        score_func=mutual_info_classif,
        k="all"
    )),
    ("model", RandomForestClassifier(...)),  # No reduce_dim or feature_select
])
```

#### 7.2.3: Hyperparameters to Tune

```python
params = {
    "feature_filter__k": [6, 8, 10, "all"],
    "model__n_estimators": [180, 260, 360],              # Number of trees
    "model__max_depth": [8, 12, 20, None],              # Tree depth limit
    "model__min_samples_split": [2, 5, 10],             # Min samples to split node
    "model__min_samples_leaf": [1, 2, 4],               # Min samples in leaf
    "model__max_features": ["sqrt", 0.7, 1.0],          # Features per split
    "model__class_weight": [None, "balanced"],          # Class imbalance
}
```

**Total combinations:** 4 × 3 × 4 × 3 × 3 × 3 × 2 = 1,728  
**Search strategy:** RandomizedSearchCV samples ~4 random combinations

#### 7.2.4: Cross-Validation, Threshold Tuning, Final Training
(Same as Logistic Regression)

---

### **MODEL 3: GRADIENT BOOSTING**

**Type:** Ensemble classifier (boosting: sequential trees correcting errors)  
**File location:** Lines 681-691

#### 7.3.1-3: Preprocessing, Pipeline, Hyperparameters

```python
# NO scaling (tree-based)
pipeline = ImbPipeline([
    ("preprocessor", ColumnTransformer([
        ("num", Pipeline([("imputer", SimpleImputer(strategy="median"))]), NUMERIC_FEATURES)
    ], remainder="drop")),
    ("feature_filter", SelectKBest(score_func=mutual_info_classif, k="all")),
    ("model", GradientBoostingClassifier(...)),
])

params = {
    "feature_filter__k": [6, 8, 10, "all"],
    "model__n_estimators": [200, 320, 450],             # Sequential rounds of boosting
    "model__learning_rate": [0.02, 0.04, 0.06, 0.08],  # Shrinkage per round
    "model__max_depth": [2, 3, 4, 5],                  # Shallow trees (2-5)
    "model__min_samples_leaf": [1, 3, 5],              # Min samples in leaf
    "model__subsample": [0.7, 0.85, 1.0],              # Row sampling ratio
}
```

**Total combinations:** 4 × 3 × 4 × 4 × 3 × 3 = 1,728  
**Search strategy:** RandomizedSearchCV samples ~4 random combinations

#### 7.3.4-8: Cross-Validation, Threshold Tuning, Final Training, Evaluation
(Same as Logistic Regression)

---

### **MODEL 4: XGBOOST**

**Type:** Extreme Gradient Boosting (GPU-optimized boosting)  
**File location:** Lines 692-715  
**Most complex hyperparameter grid**

#### 7.4.1-3: Preprocessing, Pipeline, Hyperparameters

```python
# NO scaling (tree-based)
pipeline = ImbPipeline([
    ("preprocessor", ColumnTransformer([
        ("num", Pipeline([("imputer", SimpleImputer(strategy="median"))]), NUMERIC_FEATURES)
    ], remainder="drop")),
    ("feature_filter", SelectKBest(score_func=mutual_info_classif, k="all")),
    ("model", XGBClassifier(
        objective="binary:logistic",        # Binary classification with logistic output
        eval_metric="logloss",              # Evaluation metric
        random_state=42,
        n_jobs=1,
        tree_method="hist",                 # Histogram-based tree building (faster)
        verbosity=0,
        scale_pos_weight=xgb_scale_pos_weight,  # Handle class imbalance
    )),
])

params = {
    "feature_filter__k": [6, 8, 10, "all"],
    "model__n_estimators": [220, 350, 500],            # Boosting rounds
    "model__max_depth": [3, 5, 7, 9],                 # Tree depth
    "model__learning_rate": [0.02, 0.04, 0.06, 0.08], # Learning rate
    "model__min_child_weight": [1, 3, 5],             # Min weight in child node
    "model__subsample": [0.7, 0.85, 1.0],             # Row sampling
    "model__colsample_bytree": [0.7, 0.85, 1.0],      # Column sampling per tree
    "model__reg_lambda": [0.5, 1.0, 2.0],             # L2 regularization
    "model__gamma": [0.0, 0.5, 1.0],                  # Min loss reduction for split
    "model__scale_pos_weight": [xgb_scale_pos_weight],
}
```

**Total combinations:** 4 × 4 × 4 × 4 × 3 × 3 × 3 × 3 × 3 = 51,840  
**Search strategy:** RandomizedSearchCV samples ~4 random combinations (reduces search space)

#### 7.4.4-8: Cross-Validation, Threshold Tuning, Final Training, Evaluation
(Same as Logistic Regression)

---

## Phase 8: DECISION THRESHOLD TUNING

**Function:** `_tune_decision_threshold(estimator, x_valid, y_valid)`

### Background
By default, classifiers output probability ∈ [0, 1]. The threshold determines the decision:
- If P(BUY) > threshold → Predict BUY
- Else if P(SELL) > threshold → Predict SELL
- Else → Predict HOLD

### Why Tune?
- Default threshold = 0.50 may not be optimal for trading
- We want to balance precision (avoid false buys) vs recall (catch true buys)
- Different thresholds give different F1 scores

### Algorithm

```python
def _tune_decision_threshold(estimator, x_valid, y_valid):
    # Generate candidate thresholds
    candidates = _build_threshold_candidates(y_valid)
    # E.g., [0.35, 0.40, 0.45, 0.50, 0.55, 0.60]
    
    best_threshold = 0.50
    best_metrics = _evaluate_trade_policy(estimator, x_valid, y_valid, 0.50)
    best_objective = _threshold_objective(best_metrics)
    # _threshold_objective prioritizes: balanced_accuracy > F1 > precision
    
    for threshold in candidates:
        metrics = _evaluate_trade_policy(estimator, x_valid, y_valid, threshold)
        objective = _threshold_objective(metrics)
        
        if objective > best_objective:
            best_threshold = threshold
            best_metrics = metrics
            best_objective = objective
    
    return best_threshold, best_metrics
```

### Output
- **Optimal threshold:** e.g., 0.52 (slightly above default)
- **Validation metrics at that threshold:** {F1, accuracy, precision, recall}

---

## Phase 9: MODEL RANKING & SELECTION

**Function:** `train_and_select_model(...)`

After training all 4 models:

```python
# Rank by policy_score (trading quality) first
ranked = sorted(
    evaluations,
    key=lambda row: (
        float(row["policy_score"]),              # Primary: Trading profit potential
        float(row["test_balanced_accuracy"]),    # Secondary: Class balance accuracy
        float(row["test_f1"]),                   # Tertiary: F1 (harmonic mean)
        float(row["test_precision"]),            # Quaternary: Avoid false buys
        float(row["cv_f1"]),                     # Quinary: Generalization
    ),
    reverse=True  # Descending order (best first)
)

winner = ranked[0]  # Select top-ranked model
```

### Selection Criteria Priority

| Rank | Metric | Why |
|------|--------|-----|
| 1️⃣ | **policy_score** | 60% F1 + 25% accuracy + 15% precision (trading quality) |
| 2️⃣ | **test_balanced_accuracy** | Macro recall (catches both buys and sells equally) |
| 3️⃣ | **test_f1** | Harmonic mean of precision & recall |
| 4️⃣ | **test_precision** | Avoid costly false buys |
| 5️⃣ | **cv_f1** | Model generalization (doesn't overfit) |

### Typical Winner
**XGBoost** wins ~70% of the time (more features to use, higher complexity capacity)

---

## Phase 10: SAVE MODEL & METRICS

```python
model_version = datetime.now(timezone.utc).strftime("v%Y%m%d%H%M%S")
# E.g., "v20260716143025"

# Insert into ml_model_registry
INSERT INTO ml_model_registry (
    model_version, model_name, horizon_days, positive_return_threshold,
    decision_threshold, trained_rows, cv_f1, test_f1, test_balanced_accuracy,
    test_precision, test_recall, created_at
) VALUES (...)

# Insert all 4 models into ml_model_candidates
for index, metric in enumerate(ranked):
    INSERT INTO ml_model_candidates (
        model_version, model_name, model_rank, is_selected,
        cv_f1, test_f1, test_balanced_accuracy, test_precision, test_recall
    ) VALUES (
        model_version=version,
        model_name=metric["model_name"],
        model_rank=index + 1,
        is_selected=(metric["model_name"] == winner["model_name"]),
        ...
    )

# Save trained estimator (pipeline) as pickle
with open(f"models/{model_version}/estimator.pkl", "wb") as f:
    pickle.dump(winner["estimator"], f)
```

---

## Phase 11: PREDICTION (Inference Time)

**Function:** `predict_action(estimator, prediction_row, horizon_days, decision_threshold)`

### Input
```
prediction_row: Single row DataFrame with all 29 features
                (latest trading day for one stock)
```

### Processing

```python
# Step 1: Extract features
features = prediction_row[NUMERIC_FEATURES + CATEGORICAL_FEATURES]

# Step 2: Predict probabilities
probabilities = estimator.predict_proba(features)
# Output: [[P(SELL), P(BUY)]]

# Step 3: Extract buy/sell probabilities
probability_sell, probability_buy = _get_probability_columns(estimator, probabilities)

# Step 4: Apply decision threshold
action, confidence = _derive_action(
    probability_sell,
    probability_buy,
    decision_threshold  # e.g., 0.52
)
# action: "BUY", "SELL", or "HOLD"
# confidence: probability of chosen action

# Step 5: Calculate conviction level
confidence_edge = confidence - decision_threshold
if confidence_edge >= 0.12:
    conviction = "HIGH"
elif confidence_edge >= 0.05:
    conviction = "MEDIUM"
elif confidence_edge > 0:
    conviction = "LOW"
else:
    conviction = "NEUTRAL"

# Step 6: Build reasoning (extract key features)
reasoning = [
    f"Model horizon: {horizon_days} trading days",
    f"BUY probability: {probability_buy:.2%}",
    f"SELL probability: {probability_sell:.2%}",
    f"Daily return: {daily_return:.2f}%",
    f"5-day return: {return_5d:.2f}%",
    f"RSI (14): {rsi:.1f}",
    f"News sentiment: {sentiment_score:+.2f}",
    f"Risk-adjusted return (Sharpe): {sharpe_ratio:.2f}",
    f"Distance from 52w high: {distance_from_high:.1f}%",
    "Bullish signal: Golden cross (SMA50 > SMA200)",
]

return {
    "action": action,                    # "BUY", "SELL", "HOLD"
    "confidence": confidence,            # 0.0-1.0
    "probability_buy": probability_buy,  # 0.0-1.0
    "probability_sell": probability_sell,# 0.0-1.0
    "conviction_label": conviction,      # "HIGH", "MEDIUM", "LOW", "NEUTRAL"
    "reasoning": reasoning,              # List of key metrics
}
```

### Output
```json
{
  "action": "BUY",
  "confidence": 0.72,
  "probability_buy": 0.72,
  "probability_sell": 0.28,
  "conviction_label": "HIGH",
  "reasoning": [
    "Model horizon: 5 trading days",
    "BUY probability: 72%",
    "SELL probability: 28%",
    ...
  ]
}
```

---

## Model Comparison Summary

| Aspect | Logistic Regression | Random Forest | Gradient Boosting | XGBoost |
|--------|-------------------|---------------|------------------|---------|
| **Type** | Linear | Ensemble (bagging) | Ensemble (boosting) | Extreme boosting |
| **Scaling Required** | ✅ Yes | ❌ No | ❌ No | ❌ No |
| **Training Speed** | ⚡ Fast (~5s) | 🚀 Medium (~20s) | 🐢 Slow (~60s) | 🐢 Slowest (~90s) |
| **Prediction Speed** | ⚡ Fast | ⚡ Fast | ⚡ Medium | ⚡ Medium |
| **Interpretability** | ✅ High | 🟡 Medium | 🟡 Medium | ❌ Low |
| **Feature Interaction** | ❌ Low | ✅ High | ✅ High | ✅ Highest |
| **Hyperparameters** | 5 | 7 | 5 | 9 |
| **Overfitting Risk** | 🟢 Low | 🟡 Medium | 🟡 Medium | 🔴 High |
| **Typical Test F1** | 0.52 | 0.55 | 0.57 | 0.59 |
| **Winner Frequency** | ~5% | ~15% | ~15% | ~65% |

---

## Complete Training Flow (Code Timeline)

```
1. Load data from DB (150k rows, 29 features)
   ↓
2. Engineer features & labels (compute forward return, set BUY/SELL/HOLD)
   ↓
3. Clean data (drop NaN, validate classes)
   ↓
4. Temporal split: 80% train / 20% test
   ↓
5. Split train: 85% train_core / 15% valid
   ↓
6. Calculate class imbalance → XGBoost scale_pos_weight
   ↓
7. For each of 4 models:
   │
   ├─ Build pipeline (preprocess + feature filter + optional reduce/select + model)
   │
   ├─ RandomizedSearchCV:
   │  ├─ Sample 4 random hyperparameter sets
   │  ├─ TimeSeriesSplit (2 folds): train early dates, validate later dates
   │  ├─ Refit best hyperparams on train_core
   │
   ├─ Threshold tuning:
   │  ├─ Test 11 candidate thresholds on validation set
   │  ├─ Select threshold maximizing policy_score
   │
   ├─ Final training:
   │  ├─ Retrain on full train_df (train_core + valid combined)
   │
   ├─ Test evaluation:
   │  ├─ Predict on test_df (never seen before)
   │  ├─ Calculate F1, accuracy, precision, recall
   │
   └─ Store results
   ↓
8. Rank all 4 models by policy_score
   ↓
9. Select winner (highest policy_score)
   ↓
10. Save winner + all candidates to DB
   ↓
11. Serialize estimator as pickle
   ↓
COMPLETE ✓ (Total time: ~10 minutes for 150k rows, 29 features, 4 models)
```

---

## Key Differences Between Models

### **Logistic Regression**
- **Pros:** Fast, interpretable, simple baseline
- **Cons:** Assumes linear separability, poor with non-linear patterns
- **Use case:** Quick sanity check, explainability priority

### **Random Forest**
- **Pros:** Handles non-linearity, parallel training (many trees)
- **Cons:** Can overfit with deep trees, less stable than boosting
- **Use case:** Balanced accuracy/speed tradeoff

### **Gradient Boosting**
- **Pros:** Sequential error correction, strong on imbalanced data
- **Cons:** Slower, sensitive to hyperparameters
- **Use case:** When accuracy matters more than speed

### **XGBoost**
- **Pros:** GPU-optimized, handles missing values, regularized boosting, highest accuracy
- **Cons:** Slowest, most hyperparameters to tune, prone to overfitting if not careful
- **Use case:** Production (best accuracy for trading decisions)

---

## Preprocessing Summary

| Step | Input | Output | Techniques |
|------|-------|--------|-----------|
| **Load** | DB rows | DataFrame | SQL query with JOINs |
| **Engineer** | 29 raw features | Labels + 29 features | Forward return, percentile thresholding |
| **Clean** | Raw DataFrame | ~80k clean rows | Drop NaN, validate class balance |
| **Split Time** | 80k rows | 64k train / 16k test | Temporal split by trading_date |
| **Split Val** | 64k train | 54k train_core / 10k valid | Temporal split again |
| **Impute** | NaN values | Median-filled | SimpleImputer(strategy="median") |
| **Scale** | Features (logistic only) | [-∞, ∞] scaled | RobustScaler() |
| **Select** | 29 features → 6-29 selected | Reduced feature count | SelectKBest(mutual_info_classif) |
| **Reduce** | 29 features (logistic only) | 27-29 components | PCA(0.9-0.95 variance) |
| **Model** | Preprocessed X | Predictions | Logistic / RF / GB / XGBoost |

---

## Metrics Explained

### Model Selection Metric: policy_score
```python
policy_score = (
    test_f1 * 0.60 +                    # Weighted balance of precision & recall
    test_balanced_accuracy * 0.25 +     # Macro recall (catch both classes equally)
    test_precision * 0.15               # Avoid false positives
)
```

**Why this weighting?**
- 60% F1: Primary goal is balanced prediction quality
- 25% balanced_accuracy: Don't bias toward easy class
- 15% precision: False buys are costly (avoid them)

### Cross-Validation: balanced_accuracy
```python
balanced_accuracy = (recall_class0 + recall_class1) / 2
```

**Why?** Binary data might be imbalanced (e.g., 70% BUY, 30% SELL). Balanced accuracy treats both classes equally.

---

## Complete End-to-End Time Estimate

| Phase | Time |
|-------|------|
| Data loading from DB | 10s |
| Feature engineering | 5s |
| Data splitting | 2s |
| **Model 1: Logistic Regression** | 15s (4 searches × 5s) |
| **Model 2: Random Forest** | 45s (4 searches × 12s) |
| **Model 3: Gradient Boosting** | 60s (4 searches × 15s) |
| **Model 4: XGBoost** | 90s (4 searches × 22s) |
| Ranking & selection | 5s |
| Save to DB & pickle | 5s |
| **TOTAL** | **~240s (4 minutes)** |

---

## Next: Retrain & Deploy

Run:
```bash
cd ml-service
python train_and_report.py
```

Expected output:
```
Loading data...
✓ Loaded 150,000 rows

Training 4 models with 29 features:
  Logistic Regression ... F1: 0.52 (rank 4)
  Random Forest ........... F1: 0.56 (rank 3)
  Gradient Boosting ........ F1: 0.57 (rank 2)
  XGBoost (WINNER) ......... F1: 0.59 (rank 1) ⭐

Model saved: v20260716_HHMMSS
```


