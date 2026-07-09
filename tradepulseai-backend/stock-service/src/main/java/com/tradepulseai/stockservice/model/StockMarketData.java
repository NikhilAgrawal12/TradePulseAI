package com.tradepulseai.stockservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "stock_daily_ohlc", indexes = {
        @Index(name = "idx_stock_daily_ohlc_stock_date", columnList = "stock_id,trading_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_daily_ohlc_stock_date", columnNames = {"stock_id", "trading_date"})
})
public class StockMarketData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long marketDataId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Column(name = "open_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal closePrice;

    @Column(name = "volume", nullable = false)
    private Long volume;

    @Column(name = "vwap", precision = 12, scale = 2)
    private BigDecimal vwap;

    @Column(name = "sma_20", precision = 12, scale = 2)
    private BigDecimal sma20;

    @Column(name = "sma_50", precision = 12, scale = 2)
    private BigDecimal sma50;

    @Column(name = "sma_200", precision = 12, scale = 2)
    private BigDecimal sma200;

    @Column(name = "volatility_30d", precision = 12, scale = 2)
    private BigDecimal volatility30d;

    @Column(name = "volatility_90d", precision = 12, scale = 2)
    private BigDecimal volatility90d;

    @Column(name = "daily_return_percent", precision = 12, scale = 2)
    private BigDecimal dailyReturnPercent;

    @Column(name = "is_otc", nullable = false)
    private Boolean otc;

    @Column(name = "adjusted", nullable = false)
    private Boolean adjusted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sentiment_score", precision = 5, scale = 4)
    private BigDecimal sentimentScore;

    @Column(name = "news_count")
    private Integer newsCount;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
        if (this.otc == null) {
            this.otc = false;
        }
        if (this.adjusted == null) {
            this.adjusted = true;
        }
    }

    // ...existing code...

    public BigDecimal getSentimentScore() {
        return sentimentScore;
    }

    public void setSentimentScore(BigDecimal sentimentScore) {
        this.sentimentScore = sentimentScore;
    }

    public Integer getNewsCount() {
        return newsCount;
    }

    public void setNewsCount(Integer newsCount) {
        this.newsCount = newsCount;
    }
}

