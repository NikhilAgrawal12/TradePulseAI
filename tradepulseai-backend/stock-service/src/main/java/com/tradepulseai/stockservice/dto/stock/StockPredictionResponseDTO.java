package com.tradepulseai.stockservice.dto.stock;

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
        List<String> reasoning
) {
}

