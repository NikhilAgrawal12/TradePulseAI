package com.tradepulseai.orderservice.service;

import java.math.BigDecimal;

public record StockQuote(Long stockId, String symbol, BigDecimal unitPrice) {
}

