package com.tradepulseai.stockservice.dto.stock;

public record AnalyticsNewsItemDTO(
        Long stockId,
        String symbol,
        String tradingDate,
        String news,
        Double sentimentScore,
        Integer newsCount
) {
}

