package com.tradepulse.stockservice.model;

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


    @Column(name = "volatility_5d", precision = 12, scale = 2)
    private BigDecimal volatility5d;

    @Column(name = "volatility_20d", precision = 12, scale = 2)
    private BigDecimal volatility20d;

    @Column(name = "volatility_60d", precision = 12, scale = 2)
    private BigDecimal volatility60d;

    @Column(name = "volatility_120d", precision = 12, scale = 2)
    private BigDecimal volatility120d;

    @Column(name = "volatility_90d", precision = 12, scale = 2)
    private BigDecimal volatility90d;

    @Column(name = "return_1d", precision = 12, scale = 2)
    private BigDecimal return1d;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "return_5d", precision = 12, scale = 4)
    private BigDecimal return5d;

    @Column(name = "return_90d", precision = 12, scale = 4)
    private BigDecimal return90d;

    @Column(name = "return_20d", precision = 12, scale = 4)
    private BigDecimal return20d;

    @Column(name = "return_60d", precision = 12, scale = 4)
    private BigDecimal return60d;

    @Column(name = "return_120d", precision = 12, scale = 4)
    private BigDecimal return120d;

    @Column(name = "forward_return_5d", precision = 12, scale = 4)
    private BigDecimal forwardReturn5d;

    @Column(name = "target_week_direction", length = 16)
    private String targetWeekDirection;

    @Column(name = "rsi_14", precision = 8, scale = 4)
    private BigDecimal rsi14;

    @Column(name = "macd", precision = 12, scale = 4)
    private BigDecimal macd;

    @Column(name = "macd_signal", precision = 12, scale = 4)
    private BigDecimal macdSignal;

    @Column(name = "sentiment_score", precision = 5, scale = 4)
    private BigDecimal sentimentScore;

    @Column(name = "daily_news", columnDefinition = "TEXT")
    private String dailyNews;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
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

    public BigDecimal getVolatility5d() {
        return volatility5d;
    }

    public void setVolatility5d(BigDecimal volatility5d) {
        this.volatility5d = volatility5d;
    }

    public BigDecimal getVolatility20d() {
        return volatility20d;
    }

    public void setVolatility20d(BigDecimal volatility20d) {
        this.volatility20d = volatility20d;
    }

    public BigDecimal getVolatility60d() {
        return volatility60d;
    }

    public void setVolatility60d(BigDecimal volatility60d) {
        this.volatility60d = volatility60d;
    }

    public BigDecimal getVolatility120d() {
        return volatility120d;
    }

    public void setVolatility120d(BigDecimal volatility120d) {
        this.volatility120d = volatility120d;
    }

    public BigDecimal getVolatility90d() {
        return volatility90d;
    }

    public void setVolatility90d(BigDecimal volatility90d) {
        this.volatility90d = volatility90d;
    }

    public BigDecimal getReturn1d() {
        return return1d;
    }

    public void setReturn1d(BigDecimal return1d) {
        this.return1d = return1d;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public BigDecimal getReturn5d() { return return5d; }
    public void setReturn5d(BigDecimal return5d) { this.return5d = return5d; }

    public BigDecimal getReturn90d() { return return90d; }
    public void setReturn90d(BigDecimal return90d) { this.return90d = return90d; }

    public BigDecimal getReturn20d() { return return20d; }
    public void setReturn20d(BigDecimal return20d) { this.return20d = return20d; }

    public BigDecimal getReturn60d() { return return60d; }
    public void setReturn60d(BigDecimal return60d) { this.return60d = return60d; }

    public BigDecimal getReturn120d() { return return120d; }
    public void setReturn120d(BigDecimal return120d) { this.return120d = return120d; }

    public BigDecimal getForwardReturn5d() {
        return forwardReturn5d;
    }

    public void setForwardReturn5d(BigDecimal forwardReturn5d) {
        this.forwardReturn5d = forwardReturn5d;
    }

    public String getTargetWeekDirection() {
        return targetWeekDirection;
    }

    public void setTargetWeekDirection(String targetWeekDirection) {
        this.targetWeekDirection = targetWeekDirection;
    }


    public BigDecimal getRsi14() { return rsi14; }
    public void setRsi14(BigDecimal rsi14) { this.rsi14 = rsi14; }

    public BigDecimal getMacd() { return macd; }
    public void setMacd(BigDecimal macd) { this.macd = macd; }

    public BigDecimal getMacdSignal() { return macdSignal; }
    public void setMacdSignal(BigDecimal macdSignal) { this.macdSignal = macdSignal; }

    public BigDecimal getSentimentScore() {
        return sentimentScore;
    }

    public void setSentimentScore(BigDecimal sentimentScore) {
        this.sentimentScore = sentimentScore;
    }


    public String getDailyNews() {
        return dailyNews;
    }

    public void setDailyNews(String dailyNews) {
        this.dailyNews = dailyNews;
    }
}
