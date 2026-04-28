package com.tradepulseai.custservice.dto.portfolio;

import java.math.BigDecimal;

public class PortfolioSummaryResponseDTO {

    private int totalPositions;
    private int totalQuantity;
    private BigDecimal totalInvestedValue;
    private BigDecimal totalMarketValue;
    private BigDecimal totalUnrealizedPnl;
    private BigDecimal totalUnrealizedPnlPercent;
    private BigDecimal totalRealizedPnl;

    public int getTotalPositions() {
        return totalPositions;
    }

    public void setTotalPositions(int totalPositions) {
        this.totalPositions = totalPositions;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public BigDecimal getTotalInvestedValue() {
        return totalInvestedValue;
    }

    public void setTotalInvestedValue(BigDecimal totalInvestedValue) {
        this.totalInvestedValue = totalInvestedValue;
    }

    public BigDecimal getTotalMarketValue() {
        return totalMarketValue;
    }

    public void setTotalMarketValue(BigDecimal totalMarketValue) {
        this.totalMarketValue = totalMarketValue;
    }

    public BigDecimal getTotalUnrealizedPnl() {
        return totalUnrealizedPnl;
    }

    public void setTotalUnrealizedPnl(BigDecimal totalUnrealizedPnl) {
        this.totalUnrealizedPnl = totalUnrealizedPnl;
    }

    public BigDecimal getTotalUnrealizedPnlPercent() {
        return totalUnrealizedPnlPercent;
    }

    public void setTotalUnrealizedPnlPercent(BigDecimal totalUnrealizedPnlPercent) {
        this.totalUnrealizedPnlPercent = totalUnrealizedPnlPercent;
    }

    public BigDecimal getTotalRealizedPnl() {
        return totalRealizedPnl;
    }

    public void setTotalRealizedPnl(BigDecimal totalRealizedPnl) {
        this.totalRealizedPnl = totalRealizedPnl;
    }
}


