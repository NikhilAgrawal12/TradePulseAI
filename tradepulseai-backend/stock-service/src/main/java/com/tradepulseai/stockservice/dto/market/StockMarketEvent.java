package com.tradepulseai.stockservice.dto.market;

import java.math.BigDecimal;
import java.time.Instant;

public record StockMarketEvent(
        String symbol,
        BigDecimal price,
        BigDecimal changePercent,
        long volume,
        Instant marketTimestamp,
        String source
) {
}

