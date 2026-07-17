package com.tradepulseai.stockservice.service;

/**
 * Application event emitted when a stock cache entry is updated from websocket data.
 */
public record StockCacheUpdatedEvent(Long stockId) {
}

