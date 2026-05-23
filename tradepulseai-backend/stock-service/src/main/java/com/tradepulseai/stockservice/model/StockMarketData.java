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

    @Column(name = "open_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "volume", nullable = false)
    private Long volume;

    @Column(name = "vwap", precision = 12, scale = 4)
    private BigDecimal vwap;

    @Column(name = "pre_market_price", precision = 12, scale = 4)
    private BigDecimal preMarketPrice;

    @Column(name = "after_hours_price", precision = 12, scale = 4)
    private BigDecimal afterHoursPrice;

    @Column(name = "is_otc", nullable = false)
    private Boolean otc;

    @Column(name = "adjusted", nullable = false)
    private Boolean adjusted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public BigDecimal getPreMarketPrice() {
        return preMarketPrice;
    }

    public void setPreMarketPrice(BigDecimal preMarketPrice) {
        this.preMarketPrice = preMarketPrice;
    }

    public BigDecimal getAfterHoursPrice() {
        return afterHoursPrice;
    }

    public void setAfterHoursPrice(BigDecimal afterHoursPrice) {
        this.afterHoursPrice = afterHoursPrice;
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
}

