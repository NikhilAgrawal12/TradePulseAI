package com.tradepulseai.stockservice.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "symbol", nullable = false, unique = true)
    private String symbol;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sector", nullable = false)
    private String sector;

    @Column(name = "exchange", nullable = false)
    private String exchange;

    @Column(name = "price", nullable = false)
    private double price;

    @Column(name = "change_percent", nullable = false)
    private double changePercent;

    @Column(name = "rating_score", nullable = false)
    private double ratingScore;

    @Column(name = "analyst_count", nullable = false)
    private int analystCount;

    @Column(name = "market_cap_billion", nullable = false)
    private double marketCapBillion;

    @Column(name = "volume", nullable = false)
    private long volume;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", nullable = false)
    private StockRecommendation recommendation;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stock_keywords", joinColumns = @JoinColumn(name = "stock_id"))
    @Column(name = "keyword")
    private List<String> keywords = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(double changePercent) {
        this.changePercent = changePercent;
    }

    public double getRatingScore() {
        return ratingScore;
    }

    public void setRatingScore(double ratingScore) {
        this.ratingScore = ratingScore;
    }

    public int getAnalystCount() {
        return analystCount;
    }

    public void setAnalystCount(int analystCount) {
        this.analystCount = analystCount;
    }

    public double getMarketCapBillion() {
        return marketCapBillion;
    }

    public void setMarketCapBillion(double marketCapBillion) {
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

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
}

