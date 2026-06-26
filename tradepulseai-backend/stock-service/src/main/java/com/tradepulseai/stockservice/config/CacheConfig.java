package com.tradepulseai.stockservice.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration for frequently accessed data.
 * Uses in-memory caching for featured stocks and stock metadata.
 * Transparent optimization - doesn't affect current functionality.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
            "featured_stocks",      // Cache for getFeaturedStocks()
            "stock_by_symbol",      // Cache for findBySymbol()
            "exchanges",            // Cache for exchanges (reference data)
            "stock_metrics"         // Cache for stock metrics
        );
    }
}

