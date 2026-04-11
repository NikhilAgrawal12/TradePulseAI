package com.tradepulseai.stockservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long stockId;

    @Column(name = "symbol", nullable = false, length = 50, unique = true)
    private String symbol;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "sector", nullable = false, length = 100)
    private String sector;

    @Column(name = "exchange", nullable = false, length = 100)
    private String exchange;

    @Column(name = "price", nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "change_percent", nullable = false, precision = 9, scale = 4)
    private BigDecimal changePercent;

    @Column(name = "rating_score", nullable = false, precision = 9, scale = 4)
    private BigDecimal ratingScore;

    @Column(name = "analyst_count", nullable = false)
    private int analystCount;

    @Column(name = "market_cap_billion", nullable = false, precision = 18, scale = 4)
    private BigDecimal marketCapBillion;

    @Column(name = "volume", nullable = false)
    private long volume;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", nullable = false, length = 50)
    private StockRecommendation recommendation;

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(BigDecimal changePercent) {
        this.changePercent = changePercent;
    }

    public BigDecimal getRatingScore() {
        return ratingScore;
    }

    public void setRatingScore(BigDecimal ratingScore) {
        this.ratingScore = ratingScore;
    }

    public int getAnalystCount() {
        return analystCount;
    }

    public void setAnalystCount(int analystCount) {
        this.analystCount = analystCount;
    }

    public BigDecimal getMarketCapBillion() {
        return marketCapBillion;
    }

    public void setMarketCapBillion(BigDecimal marketCapBillion) {
        this.marketCapBillion = marketCapBillion;
    }

    public long getVolume() {
        return volume;
    }

    public void setVolume(long volume) {
        this.volume = volume;
    }

    public StockRecommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(StockRecommendation recommendation) {
        this.recommendation = recommendation;
    }
}
