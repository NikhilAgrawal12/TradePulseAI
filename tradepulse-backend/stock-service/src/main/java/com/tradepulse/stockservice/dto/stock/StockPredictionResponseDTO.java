package com.tradepulse.stockservice.dto.stock;

import java.util.List;

public record StockPredictionResponseDTO(
        Long stockId,
        String symbol,
        String action,
        Double confidence,
        Double probabilityBuy,
        Double probabilitySell,
        Integer horizonDays,
        String modelName,
        String modelVersion,
        String generatedAt,
        List<String> reasoning,
        Double decisionThreshold,
        Double confidenceEdge,
        Double probabilityGap,
        String convictionLabel,
        Double cvF1,
        Double testF1,
        Double testBalancedAccuracy,
        Double testPrecision,
        Double testRecall
) {
}

