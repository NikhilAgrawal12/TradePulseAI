package com.tradepulseai.stockservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Caches the latest aggregate data for ALL stocks in the database.
 * This allows searching and viewing stock prices after market close.
 *
 * Aggregate data includes: open, close, high, low, volume, vwap, change_percent
 * This is per-second aggregate data from Massive.io (matching frontend websocket data).
 *
 * Updated in real-time as per-second aggregates arrive from the websocket feed.
 * AllStocksLastValueCacheService subscribes to all 800+ stock symbols and immediately
 * overwrites the cache entry for each stock as new bars are published.
 */
@Entity
@Table(name = "all_stocks_last_value_cache", uniqueConstraints = {
        @UniqueConstraint(columnNames = "stock_id", name = "uk_all_stocks_last_value_stock_id")
})
public class AllStocksLastValueCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cache_id")
    private Long cacheId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "cached_open", nullable = false, precision = 14, scale = 2)
    private BigDecimal cachedOpen;

    @Column(name = "cached_close", nullable = false, precision = 14, scale = 2)
    private BigDecimal cachedClose;

    @Column(name = "cached_high", nullable = false, precision = 14, scale = 2)
    private BigDecimal cachedHigh;

    @Column(name = "cached_low", nullable = false, precision = 14, scale = 2)
    private BigDecimal cachedLow;

    @Column(name = "cached_volume", nullable = false)
    private Long cachedVolume;

    @Column(name = "cached_vwap", nullable = false, precision = 14, scale = 2)
    private BigDecimal cachedVwap;

    @Column(name = "cached_change_percent", precision = 12, scale = 2)
    private BigDecimal cachedChangePercent;

    @Column(name = "aggregate_updated_at")
    private Instant aggregateUpdatedAt;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @PrePersist
    public void prePersist() {
        this.cachedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.cachedAt = Instant.now();
    }

    // Getters and Setters
    public Long getCacheId() {
        return cacheId;
    }

    public void setCacheId(Long cacheId) {
        this.cacheId = cacheId;
    }

    public Stock getStock() {
        return stock;
    }

    public void setStock(Stock stock) {
        this.stock = stock;
    }

    public BigDecimal getCachedOpen() {
        return cachedOpen;
    }

    public void setCachedOpen(BigDecimal cachedOpen) {
        this.cachedOpen = cachedOpen;
    }

    public BigDecimal getCachedClose() {
        return cachedClose;
    }

    public void setCachedClose(BigDecimal cachedClose) {
        this.cachedClose = cachedClose;
    }

    public BigDecimal getCachedHigh() {
        return cachedHigh;
    }

    public void setCachedHigh(BigDecimal cachedHigh) {
        this.cachedHigh = cachedHigh;
    }

    public BigDecimal getCachedLow() {
        return cachedLow;
    }

    public void setCachedLow(BigDecimal cachedLow) {
        this.cachedLow = cachedLow;
    }

    public Long getCachedVolume() {
        return cachedVolume;
    }

    public void setCachedVolume(Long cachedVolume) {
        this.cachedVolume = cachedVolume;
    }

    public BigDecimal getCachedVwap() {
        return cachedVwap;
    }

    public void setCachedVwap(BigDecimal cachedVwap) {
        this.cachedVwap = cachedVwap;
    }

    public BigDecimal getCachedChangePercent() {
        return cachedChangePercent;
    }

    public void setCachedChangePercent(BigDecimal cachedChangePercent) {
        this.cachedChangePercent = cachedChangePercent;
    }

    public Instant getAggregateUpdatedAt() {
        return aggregateUpdatedAt;
    }

    public void setAggregateUpdatedAt(Instant aggregateUpdatedAt) {
        this.aggregateUpdatedAt = aggregateUpdatedAt;
    }

    public Instant getCachedAt() {
        return cachedAt;
    }

    public void setCachedAt(Instant cachedAt) {
        this.cachedAt = cachedAt;
    }
}

