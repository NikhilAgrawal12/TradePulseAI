package com.tradepulseai.stockservice.dto.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

public class StockDailyOhlcDTO {

    private Long id;
    private String stockId;
    private String symbol;
    private LocalDate tradingDate;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private Long volume;
    private BigDecimal vwap;
    private BigDecimal preMarketPrice;
    private BigDecimal afterHoursPrice;
    private Boolean otc;
    private Boolean adjusted;

    public StockDailyOhlcDTO() {
    }

    public StockDailyOhlcDTO(Long id, String stockId, String symbol, LocalDate tradingDate,
                            BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
                            BigDecimal closePrice, Long volume) {
        this.id = id;
        this.stockId = stockId;
        this.symbol = symbol;
        this.tradingDate = tradingDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
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
}

