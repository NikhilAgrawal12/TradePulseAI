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

import java.time.Instant;

/**
 * Holds the top 50 featured stocks ranking.
 * This is a cache table that gets refreshed daily.
 * The stocks table itself is never modified - only market cap and other frequently-changing values are updated.
 */
@Entity
@Table(name = "featured_stocks_cache", uniqueConstraints = {
        @UniqueConstraint(columnNames = "stock_id", name = "uk_featured_stock_id")
})
public class FeaturedStockCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cache_id")
    private Long cacheId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCachedAt() {
        return cachedAt;
    }

    public void setCachedAt(Instant cachedAt) {
        this.cachedAt = cachedAt;
    }
}

