from pydantic import BaseModel, Field


class TrainRequest(BaseModel):
    days_back: int = Field(default=730, ge=180, le=365 * 10)
    horizon_days: int = Field(default=5, ge=1, le=30)
    positive_return_threshold: float = Field(default=0.015, ge=0.0, le=0.2)
    neutral_return_band: float = Field(default=0.015, ge=0.0, le=0.2)


class ModelMetrics(BaseModel):
    model_name: str
    cv_f1: float
    test_f1: float
    test_balanced_accuracy: float
    test_precision: float
    test_recall: float


class TrainResponse(BaseModel):
    selected_model: str
    trained_rows: int
    horizon_days: int
    positive_return_threshold: float
    neutral_return_band: float
    metrics: list[ModelMetrics]


class PredictionResponse(BaseModel):
    stockId: int
    symbol: str
    action: str
    confidence: float
    probabilityBuy: float
    probabilitySell: float
    horizonDays: int
    modelName: str
    modelVersion: str
    generatedAt: str
    reasoning: list[str]
    decisionThreshold: float
    confidenceEdge: float
    probabilityGap: float
    convictionLabel: str
    cvF1: float | None = None
    testF1: float | None = None
    testBalancedAccuracy: float | None = None
    testPrecision: float | None = None
    testRecall: float | None = None


