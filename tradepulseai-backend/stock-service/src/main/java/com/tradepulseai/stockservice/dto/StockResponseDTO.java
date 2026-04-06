package com.tradepulseai.stockservice.dto;

import com.tradepulseai.stockservice.model.StockRecommendation;

import java.util.List;

public class StockResponseDTO {

    private String id;
    private String symbol;
    private String name;
    private String sector;
    private String exchange;
    private double price;
    private double changePercent;
    private StockRatingDTO rating;
    private double marketCapBillion;
    private long volume;
    private StockRecommendation recommendation;
    private List<String> keywords;

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

    public StockRatingDTO getRating() {
        return rating;
    }

    public void setRating(StockRatingDTO rating) {
        this.rating = rating;
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

