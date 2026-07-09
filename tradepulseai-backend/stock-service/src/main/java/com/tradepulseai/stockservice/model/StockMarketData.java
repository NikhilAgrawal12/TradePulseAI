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

    @Column(name = "daily_sentiment", length = 20)
    private String dailySentiment;

    @Column(name = "sentiment_score", precision = 5, scale = 4)
    private BigDecimal sentimentScore;

    @Column(name = "news_count")
    private Integer newsCount;

    @Column(name = "news_summary", columnDefinition = "TEXT")
    private String newsSummary;

    @Column(name = "news_sources", length = 500)
    private String newsSources;

    @Column(name = "sentiment_reasoning", columnDefinition = "TEXT")
    private String sentimentReasoning;

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

    public Long getMarketDataId() {
        return marketDataId;
    }

    public void setMarketDataId(Long marketDataId) {
        this.marketDataId = marketDataId;
    }

    public Stock getStock() {
        return stock;
    }

    public void setStock(Stock stock) {
        this.stock = stock;
    }

    public LocalDate getTradingDate() {
        return tradingDate;
    }

    public void setTradingDate(LocalDate tradingDate) {
        this.tradingDate = tradingDate;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public BigDecimal getVwap() {
        return vwap;
    }

    public void setVwap(BigDecimal vwap) {
        this.vwap = vwap;
    }

    public BigDecimal getSma20() {
        return sma20;
    }

    public void setSma20(BigDecimal sma20) {
        this.sma20 = sma20;
    }

    public BigDecimal getSma50() {
        return sma50;
    }

    public void setSma50(BigDecimal sma50) {
        this.sma50 = sma50;
    }

    public BigDecimal getSma200() {
        return sma200;
    }

    public void setSma200(BigDecimal sma200) {
        this.sma200 = sma200;
    }

    public BigDecimal getVolatility30d() {
        return volatility30d;
    }

    public void setVolatility30d(BigDecimal volatility30d) {
        this.volatility30d = volatility30d;
    }

    public BigDecimal getVolatility90d() {
        return volatility90d;
    }

    public void setVolatility90d(BigDecimal volatility90d) {
        this.volatility90d = volatility90d;
    }

    public BigDecimal getDailyReturnPercent() {
        return dailyReturnPercent;
    }

    public void setDailyReturnPercent(BigDecimal dailyReturnPercent) {
        this.dailyReturnPercent = dailyReturnPercent;
    }

    public Boolean getOtc() {
        return otc;
    }

    public void setOtc(Boolean otc) {
        this.otc = otc;
    }

    public Boolean getAdjusted() {
        return adjusted;
    }

    public void setAdjusted(Boolean adjusted) {
        this.adjusted = adjusted;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getDailySentiment() {
        return dailySentiment;
    }

    public void setDailySentiment(String dailySentiment) {
        this.dailySentiment = dailySentiment;
    }

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

    public String getNewsSummary() {
        return newsSummary;
    }

    public void setNewsSummary(String newsSummary) {
        this.newsSummary = newsSummary;
    }

    public String getNewsSources() {
        return newsSources;
    }

    public void setNewsSources(String newsSources) {
        this.newsSources = newsSources;
    }

    public String getSentimentReasoning() {
        return sentimentReasoning;
    }

    public void setSentimentReasoning(String sentimentReasoning) {
        this.sentimentReasoning = sentimentReasoning;
    }
}
