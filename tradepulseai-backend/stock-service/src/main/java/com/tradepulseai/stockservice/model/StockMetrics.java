package com.tradepulseai.stockservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stock_metrics")
public class StockMetrics {

    @Id
    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "current_price", precision = 14, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "week_return", precision = 12, scale = 2)
    private BigDecimal weekReturn;

    @Column(name = "month_return", precision = 12, scale = 2)
    private BigDecimal monthReturn;

    @Column(name = "year_return", precision = 12, scale = 2)
    private BigDecimal yearReturn;

    @Column(name = "volatility_30d", precision = 12, scale = 2)
    private BigDecimal volatility30d;

    @Column(name = "volatility_90d", precision = 12, scale = 2)
    private BigDecimal volatility90d;

    @Column(name = "avg_volume_30d", precision = 20, scale = 2)
    private BigDecimal avgVolume30d;

    @Column(name = "high_52w", precision = 14, scale = 2)
    private BigDecimal high52w;

    @Column(name = "low_52w", precision = 14, scale = 2)
    private BigDecimal low52w;

    @Column(name = "rsi_14", precision = 12, scale = 2)
    private BigDecimal rsi14;

    @Column(name = "sma_20", precision = 14, scale = 2)
    private BigDecimal sma20;

    @Column(name = "sma_50", precision = 14, scale = 2)
    private BigDecimal sma50;

    @Column(name = "sma_200", precision = 14, scale = 2)
    private BigDecimal sma200;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getWeekReturn() {
        return weekReturn;
    }

    public void setWeekReturn(BigDecimal weekReturn) {
        this.weekReturn = weekReturn;
    }

    public BigDecimal getMonthReturn() {
        return monthReturn;
    }

    public void setMonthReturn(BigDecimal monthReturn) {
        this.monthReturn = monthReturn;
    }

    public BigDecimal getYearReturn() {
        return yearReturn;
    }

    public void setYearReturn(BigDecimal yearReturn) {
        this.yearReturn = yearReturn;
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

    public BigDecimal getAvgVolume30d() {
        return avgVolume30d;
    }

    public void setAvgVolume30d(BigDecimal avgVolume30d) {
        this.avgVolume30d = avgVolume30d;
    }

    public BigDecimal getHigh52w() {
        return high52w;
    }

    public void setHigh52w(BigDecimal high52w) {
        this.high52w = high52w;
    }

    public BigDecimal getLow52w() {
        return low52w;
    }

    public void setLow52w(BigDecimal low52w) {
        this.low52w = low52w;
    }

    public BigDecimal getRsi14() {
        return rsi14;
    }

    public void setRsi14(BigDecimal rsi14) {
        this.rsi14 = rsi14;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
