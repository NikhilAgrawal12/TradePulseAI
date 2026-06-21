package com.tradepulseai.stockservice.dto.stock;

import java.util.List;

public record StockInsightsResponseDTO(
        String id,
        String symbol,
        String name,
        String exchange,
        String market,
        String lastUpdated,
        CurrentPerformanceDTO currentPerformance,
        Metrics52WeekDTO metrics52Week,
        ReturnMetricsDTO returns,
        VolumeMetricsDTO volumeMetrics,
        VolatilityMetricsDTO volatilityMetrics,
        TrendMetricsDTO trendMetrics,
        MomentumMetricsDTO momentumMetrics,
        RiskMetricsDTO riskMetrics,
        PerformanceDistributionDTO performanceDistribution,
        VolumeDistributionDTO volumeDistribution,
        DrawdownAnalysisDTO drawdownAnalysis,
        BestWorstDaysDTO bestWorstDays,
        List<MonthlyReturnHeatmapCellDTO> monthlyReturnsHeatmap,
        List<StockHistoryPointDTO> history
) {
    public record CurrentPerformanceDTO(
            Double currentPrice,
            Double previousClose,
            Double dailyChange,
            Double dailyChangePercent
    ) {
    }

    public record Metrics52WeekDTO(
            Double high52Week,
            Double low52Week,
            Double distanceFromHighPercent,
            Double distanceFromLowPercent
    ) {
    }

    public record ReturnMetricsDTO(
            Double oneWeekReturn,
            Double oneMonthReturn,
            Double threeMonthReturn,
            Double sixMonthReturn,
            Double oneYearReturn,
            Double threeYearReturn
    ) {
    }

    public record VolumeMetricsDTO(
            Long latestTradingDayVolume,
            String latestTradingDate,
            Double average30DayVolume,
            Double relativeVolume
    ) {
    }

    public record VolatilityMetricsDTO(
            Double volatility30Day,
            Double volatility90Day,
            Double volatility1Year
    ) {
    }

    public record TrendMetricsDTO(
            Double sma20,
            Double sma50,
            Double sma200,
            Boolean goldenCross,
            Boolean deathCross
    ) {
    }

    public record MomentumMetricsDTO(
            Double rsi14,
            Double macd,
            Double macdSignal,
            Double momentum30Day
    ) {
    }

    public record RiskMetricsDTO(
            Double sharpeRatio,
            Double sortinoRatio,
            Double maxDrawdown,
            Double betaVsSp500
    ) {
    }

    public record PerformanceDistributionDTO(
            Integer positiveDays,
            Integer negativeDays,
            Integer flatDays
    ) {
    }

    public record VolumeDistributionDTO(
            Long minVolume,
            Double averageVolume,
            Long maxVolume
    ) {
    }

    public record DrawdownAnalysisDTO(
            Double maxDrawdown,
            String peakDate,
            String troughDate
    ) {
    }

    public record BestWorstDaysDTO(
            Double bestDailyGain,
            String bestDailyGainDate,
            Double worstDailyLoss,
            String worstDailyLossDate
    ) {
    }

    public record MonthlyReturnHeatmapCellDTO(
            int year,
            int month,
            Double returnPercent
    ) {
    }

    public record StockHistoryPointDTO(
            String tradingDate,
            Double open,
            Double high,
            Double low,
            Double close,
            Long volume,
            Double sma20,
            Double sma50,
            Double sma200,
            Double volatility30Day,
            Double volatility90Day,
            Double volatility1Year,
            Double dailyReturnPercent
    ) {
    }
}

