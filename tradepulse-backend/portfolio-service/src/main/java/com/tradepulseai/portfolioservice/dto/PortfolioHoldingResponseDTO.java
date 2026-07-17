package com.tradepulseai.portfolioservice.dto;

import java.math.BigDecimal;

public class PortfolioHoldingResponseDTO {
    private String stockId;
    private int quantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal currentPrice;
    private BigDecimal investedValue;
    private BigDecimal marketValue;
    private BigDecimal unrealizedPnl;
    private BigDecimal unrealizedPnlPercent;
    private BigDecimal realizedPnl;

    public String getStockId() { return stockId; }
    public void setStockId(String stockId) { this.stockId = stockId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getAverageBuyPrice() { return averageBuyPrice; }
    public void setAverageBuyPrice(BigDecimal averageBuyPrice) { this.averageBuyPrice = averageBuyPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public BigDecimal getInvestedValue() { return investedValue; }
    public void setInvestedValue(BigDecimal investedValue) { this.investedValue = investedValue; }
    public BigDecimal getMarketValue() { return marketValue; }
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }
    public BigDecimal getUnrealizedPnlPercent() { return unrealizedPnlPercent; }
    public void setUnrealizedPnlPercent(BigDecimal unrealizedPnlPercent) { this.unrealizedPnlPercent = unrealizedPnlPercent; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
}

