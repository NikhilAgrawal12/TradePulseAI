package com.tradepulseai.stockservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "stock_metrics")
public class StockMetrics {

    @Id
    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "week_return", precision = 12, scale = 2)
    private BigDecimal weekReturn;

    @Column(name = "month_return", precision = 12, scale = 2)
    private BigDecimal monthReturn;

    @Column(name = "year_return", precision = 12, scale = 2)
    private BigDecimal yearReturn;

    @Column(name = "three_month_return", precision = 12, scale = 2)
    private BigDecimal threeMonthReturn;

    @Column(name = "six_month_return", precision = 12, scale = 2)
    private BigDecimal sixMonthReturn;

    @Column(name = "three_year_return", precision = 12, scale = 2)
    private BigDecimal threeYearReturn;

    @Column(name = "high_52w", precision = 14, scale = 2)
    private BigDecimal high52w;

    @Column(name = "low_52w", precision = 14, scale = 2)
    private BigDecimal low52w;

    @Column(name = "distance_from_high_percent", precision = 12, scale = 2)
    private BigDecimal distanceFromHighPercent;

    @Column(name = "distance_from_low_percent", precision = 12, scale = 2)
    private BigDecimal distanceFromLowPercent;

    @Column(name = "avg_volume_30d", precision = 20, scale = 2)
    private BigDecimal avgVolume30d;

    @Column(name = "latest_trading_day_volume")
    private Long latestTradingDayVolume;

    @Column(name = "latest_trading_date")
    private LocalDate latestTradingDate;

    @Column(name = "relative_volume", precision = 12, scale = 4)
    private BigDecimal relativeVolume;

    @Column(name = "volatility_30d", precision = 12, scale = 2)
    private BigDecimal volatility30d;

    @Column(name = "volatility_90d", precision = 12, scale = 2)
    private BigDecimal volatility90d;

    @Column(name = "positive_days_1y")
    private Integer positiveDays1y;

    @Column(name = "negative_days_1y")
    private Integer negativeDays1y;

    @Column(name = "flat_days_1y")
    private Integer flatDays1y;

    @Column(name = "monthly_returns_heatmap", columnDefinition = "TEXT")
    private String monthlyReturnsHeatmap;

    @Column(name = "max_drawdown", precision = 12, scale = 2)
    private BigDecimal maxDrawdown;

    @Column(name = "drawdown_peak_date")
    private LocalDate drawdownPeakDate;

    @Column(name = "drawdown_trough_date")
    private LocalDate drawdownTroughDate;

    @Column(name = "sharpe_ratio", precision = 12, scale = 2)
    private BigDecimal sharpeRatio;

    @Column(name = "sortino_ratio", precision = 12, scale = 2)
    private BigDecimal sortinoRatio;

    @Column(name = "rsi_14", precision = 12, scale = 2)
    private BigDecimal rsi14;

    @Column(name = "macd", precision = 12, scale = 2)
    private BigDecimal macd;

    @Column(name = "macd_signal", precision = 12, scale = 2)
    private BigDecimal macdSignal;

    @Column(name = "momentum_30d", precision = 12, scale = 2)
    private BigDecimal momentum30d;

    @Column(name = "golden_cross")
    private Boolean goldenCross;

    @Column(name = "death_cross")
    private Boolean deathCross;

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

    public BigDecimal getThreeMonthReturn() {
        return threeMonthReturn;
    }

    public void setThreeMonthReturn(BigDecimal threeMonthReturn) {
        this.threeMonthReturn = threeMonthReturn;
    }

    public BigDecimal getSixMonthReturn() {
        return sixMonthReturn;
    }

    public void setSixMonthReturn(BigDecimal sixMonthReturn) {
        this.sixMonthReturn = sixMonthReturn;
    }

    public BigDecimal getThreeYearReturn() {
        return threeYearReturn;
    }

    public void setThreeYearReturn(BigDecimal threeYearReturn) {
        this.threeYearReturn = threeYearReturn;
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

    public BigDecimal getDistanceFromHighPercent() {
        return distanceFromHighPercent;
    }

    public void setDistanceFromHighPercent(BigDecimal distanceFromHighPercent) {
        this.distanceFromHighPercent = distanceFromHighPercent;
    }

    public BigDecimal getDistanceFromLowPercent() {
        return distanceFromLowPercent;
    }

    public void setDistanceFromLowPercent(BigDecimal distanceFromLowPercent) {
        this.distanceFromLowPercent = distanceFromLowPercent;
    }

    public BigDecimal getAvgVolume30d() {
        return avgVolume30d;
    }

    public void setAvgVolume30d(BigDecimal avgVolume30d) {
        this.avgVolume30d = avgVolume30d;
    }

    public Long getLatestTradingDayVolume() {
        return latestTradingDayVolume;
    }

    public void setLatestTradingDayVolume(Long latestTradingDayVolume) {
        this.latestTradingDayVolume = latestTradingDayVolume;
    }

    public LocalDate getLatestTradingDate() {
        return latestTradingDate;
    }

    public void setLatestTradingDate(LocalDate latestTradingDate) {
        this.latestTradingDate = latestTradingDate;
    }

    public BigDecimal getRelativeVolume() {
        return relativeVolume;
    }

    public void setRelativeVolume(BigDecimal relativeVolume) {
        this.relativeVolume = relativeVolume;
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


    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Integer getPositiveDays1y() {
        return positiveDays1y;
    }

    public void setPositiveDays1y(Integer positiveDays1y) {
        this.positiveDays1y = positiveDays1y;
    }

    public Integer getNegativeDays1y() {
        return negativeDays1y;
    }

    public void setNegativeDays1y(Integer negativeDays1y) {
        this.negativeDays1y = negativeDays1y;
    }

    public Integer getFlatDays1y() {
        return flatDays1y;
    }

    public void setFlatDays1y(Integer flatDays1y) {
        this.flatDays1y = flatDays1y;
    }

    public String getMonthlyReturnsHeatmap() {
        return monthlyReturnsHeatmap;
    }

    public void setMonthlyReturnsHeatmap(String monthlyReturnsHeatmap) {
        this.monthlyReturnsHeatmap = monthlyReturnsHeatmap;
    }

    public BigDecimal getMaxDrawdown() {
        return maxDrawdown;
    }

    public void setMaxDrawdown(BigDecimal maxDrawdown) {
        this.maxDrawdown = maxDrawdown;
    }

    public LocalDate getDrawdownPeakDate() {
        return drawdownPeakDate;
    }

    public void setDrawdownPeakDate(LocalDate drawdownPeakDate) {
        this.drawdownPeakDate = drawdownPeakDate;
    }

    public LocalDate getDrawdownTroughDate() {
        return drawdownTroughDate;
    }

    public void setDrawdownTroughDate(LocalDate drawdownTroughDate) {
        this.drawdownTroughDate = drawdownTroughDate;
    }

    public BigDecimal getSharpeRatio() {
        return sharpeRatio;
    }

    public void setSharpeRatio(BigDecimal sharpeRatio) {
        this.sharpeRatio = sharpeRatio;
    }

    public BigDecimal getSortinoRatio() {
        return sortinoRatio;
    }

    public void setSortinoRatio(BigDecimal sortinoRatio) {
        this.sortinoRatio = sortinoRatio;
    }

    public BigDecimal getRsi14() {
        return rsi14;
    }

    public void setRsi14(BigDecimal rsi14) {
        this.rsi14 = rsi14;
    }

    public BigDecimal getMacd() {
        return macd;
    }

    public void setMacd(BigDecimal macd) {
        this.macd = macd;
    }

    public BigDecimal getMacdSignal() {
        return macdSignal;
    }

    public void setMacdSignal(BigDecimal macdSignal) {
        this.macdSignal = macdSignal;
    }

    public BigDecimal getMomentum30d() {
        return momentum30d;
    }

    public void setMomentum30d(BigDecimal momentum30d) {
        this.momentum30d = momentum30d;
    }

    public Boolean getGoldenCross() {
        return goldenCross;
    }

    public void setGoldenCross(Boolean goldenCross) {
        this.goldenCross = goldenCross;
    }

    public Boolean getDeathCross() {
        return deathCross;
    }

    public void setDeathCross(Boolean deathCross) {
        this.deathCross = deathCross;
    }
}
