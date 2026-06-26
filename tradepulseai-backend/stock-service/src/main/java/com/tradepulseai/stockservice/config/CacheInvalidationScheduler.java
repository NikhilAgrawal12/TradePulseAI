package com.tradepulseai.stockservice.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Cache invalidation scheduler for featured stocks.
 * Clears featured stocks cache daily to ensure fresh data from database.
 * Transparent optimization - doesn't affect current functionality.
 */
@Configuration
@EnableScheduling
public class CacheInvalidationScheduler {

    private final CacheManager cacheManager;

    public CacheInvalidationScheduler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Clear featured stocks cache daily at 8:55 AM (5 minutes before daily refresh at 9 AM)
     * Ensures fresh data is fetched from database after ranking calculation
     */
    @Scheduled(cron = "0 55 8 * * ?", zone = "UTC")
    public void clearFeaturedStocksCache() {
        var cache = cacheManager.getCache("featured_stocks");
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * Clear stock symbol cache hourly to ensure relatively fresh data
     */
    @Scheduled(cron = "0 0 * * * ?", zone = "UTC")
    public void clearStockBySymbolCache() {
        var cache = cacheManager.getCache("stock_by_symbol");
        if (cache != null) {
            cache.clear();
        }
    }
}

