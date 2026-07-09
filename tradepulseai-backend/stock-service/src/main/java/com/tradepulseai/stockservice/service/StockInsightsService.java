package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.stock.AnalyticsNewsItemDTO;
import com.tradepulseai.stockservice.dto.stock.StockInsightsResponseDTO;
import com.tradepulseai.stockservice.exception.StockNotFoundException;
import com.tradepulseai.stockservice.model.AllStocksLastValueCache;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.model.StockMetrics;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import com.tradepulseai.stockservice.repository.StockMetricsRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
@Service
public class StockInsightsService {

    private static final int HISTORY_LOOKBACK_ROWS = 800;
    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final StockRepository stockRepository;
    private final StockMarketDataRepository stockMarketDataRepository;
    private final StockMetricsRepository stockMetricsRepository;
    private final AllStocksLastValueCacheService allStocksLastValueCacheService;

    public StockInsightsService(
            StockRepository stockRepository,
            StockMarketDataRepository stockMarketDataRepository,
            StockMetricsRepository stockMetricsRepository,
            AllStocksLastValueCacheService allStocksLastValueCacheService
    ) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.stockMetricsRepository = stockMetricsRepository;
        this.allStocksLastValueCacheService = allStocksLastValueCacheService;
    }

    public StockInsightsResponseDTO getInsights(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException("Stock not found with id: " + stockId));

        List<StockMarketData> historyAsc = stockMarketDataRepository
                .findRecentByStockId(stockId, PageRequest.of(0, HISTORY_LOOKBACK_ROWS));
        if (historyAsc.isEmpty()) {
            throw new StockNotFoundException("Stock insights not available yet for id: " + stockId);
        }
        historyAsc = new ArrayList<>(historyAsc);
        historyAsc.sort(Comparator.comparing(StockMarketData::getTradingDate));

        StockMarketData latestHistorical = historyAsc.getLast();
        StockMarketData priorHistorical = historyAsc.size() > 1 ? historyAsc.get(historyAsc.size() - 2) : null;
        AllStocksLastValueCache realtime = allStocksLastValueCacheService.getCacheEntryByStockId(stockId);
        StockMetrics metrics = stockMetricsRepository.findById(stockId).orElse(null);

        // Current Performance — driven by realtime cache; falls back to latest daily OHLC when market is closed
        BigDecimal currentPrice = resolveCurrentPrice(realtime, latestHistorical);
        BigDecimal previousClose = resolvePreviousClose(realtime, latestHistorical, priorHistorical);
        BigDecimal dailyChange = currentPrice != null && previousClose != null ? currentPrice.subtract(previousClose) : null;
        BigDecimal dailyChangePercent = resolveDailyChangePercent(realtime, previousClose, currentPrice);

        BigDecimal high52Week = metrics == null ? null : metrics.getHigh52w();
        BigDecimal low52Week = metrics == null ? null : metrics.getLow52w();
        BigDecimal distanceFromHighPercent = metrics == null ? null : metrics.getDistanceFromHighPercent();
        BigDecimal distanceFromLowPercent = metrics == null ? null : metrics.getDistanceFromLowPercent();

        BigDecimal oneWeekReturn = metrics == null ? null : metrics.getWeekReturn();
        BigDecimal oneMonthReturn = metrics == null ? null : metrics.getMonthReturn();
        BigDecimal threeMonthReturn = metrics == null ? null : metrics.getThreeMonthReturn();
        BigDecimal sixMonthReturn = metrics == null ? null : metrics.getSixMonthReturn();
        BigDecimal oneYearReturn = metrics == null ? null : metrics.getYearReturn();
        BigDecimal threeYearReturn = metrics == null ? null : metrics.getThreeYearReturn();

        long latestTradingDayVolume = metrics != null && metrics.getLatestTradingDayVolume() != null
                ? metrics.getLatestTradingDayVolume()
                : resolveLatestTradingDayVolume(latestHistorical);
        String latestTradingDate = metrics != null && metrics.getLatestTradingDate() != null
                ? metrics.getLatestTradingDate().toString()
                : latestHistorical.getTradingDate() == null ? null : latestHistorical.getTradingDate().toString();
        BigDecimal average30DayVolume = metrics == null ? null : metrics.getAvgVolume30d();
        BigDecimal relativeVolume = metrics == null ? null : metrics.getRelativeVolume();

        BigDecimal volatility30Day = metrics == null ? null : metrics.getVolatility30d();
        BigDecimal volatility90Day = metrics == null ? null : metrics.getVolatility90d();

        BigDecimal sma20 = latestHistorical.getSma20();
        BigDecimal sma50 = latestHistorical.getSma50();
        BigDecimal sma200 = latestHistorical.getSma200();
        Boolean goldenCross = metrics == null ? null : metrics.getGoldenCross();
        Boolean deathCross = metrics == null ? null : metrics.getDeathCross();

        BigDecimal rsi14 = metrics == null ? null : metrics.getRsi14();
        MacdResult macdResult = new MacdResult(
                metrics == null ? null : metrics.getMacd(),
                metrics == null ? null : metrics.getMacdSignal());
        BigDecimal momentum30Day = metrics == null ? null : metrics.getMomentum30d();
        RiskSummary riskSummary = new RiskSummary(
                metrics == null ? null : metrics.getSharpeRatio(),
                metrics == null ? null : metrics.getSortinoRatio());
        DrawdownSummary drawdownSummary = resolveDrawdownSummary(metrics);
        DistributionSummary distributionSummary = resolveDistributionSummary(metrics);
        List<StockInsightsResponseDTO.DailyNewsDTO> latestNews = buildLatestNews(historyAsc);
        List<StockInsightsResponseDTO.MonthlyReturnHeatmapCellDTO> monthlyReturnsHeatmap = resolveMonthlyReturnsHeatmap(metrics);
        List<StockInsightsResponseDTO.StockHistoryPointDTO> historyPoints = buildHistoryPoints(historyAsc);

        return new StockInsightsResponseDTO(
                String.valueOf(stock.getStockId()),
                stock.getSymbol(),
                stock.getName(),
                resolveExchange(stock),
                stock.getMarket(),
                resolveLastUpdated(realtime, latestHistorical),
                new StockInsightsResponseDTO.CurrentPerformanceDTO(
                        toDouble(currentPrice),
                        toDouble(previousClose),
                        toDouble(dailyChange),
                        toDouble(dailyChangePercent)
                ),
                new StockInsightsResponseDTO.Metrics52WeekDTO(
                        toDouble(high52Week),
                        toDouble(low52Week),
                        toDouble(distanceFromHighPercent),
                        toDouble(distanceFromLowPercent)
                ),
                new StockInsightsResponseDTO.ReturnMetricsDTO(
                        toDouble(oneWeekReturn),
                        toDouble(oneMonthReturn),
                        toDouble(threeMonthReturn),
                        toDouble(sixMonthReturn),
                        toDouble(oneYearReturn),
                        toDouble(threeYearReturn)
                ),
                new StockInsightsResponseDTO.VolumeMetricsDTO(
                        latestTradingDayVolume,
                        latestTradingDate,
                        toDouble(average30DayVolume),
                        toDouble(relativeVolume)
                ),
                new StockInsightsResponseDTO.VolatilityMetricsDTO(
                        toDouble(volatility30Day),
                        toDouble(volatility90Day)
                ),
                new StockInsightsResponseDTO.TrendMetricsDTO(
                        toDouble(sma20),
                        toDouble(sma50),
                        toDouble(sma200),
                        goldenCross,
                        deathCross
                ),
                new StockInsightsResponseDTO.MomentumMetricsDTO(
                        toDouble(rsi14),
                        toDouble(macdResult.macd()),
                        toDouble(macdResult.signal()),
                        toDouble(momentum30Day)
                ),
                new StockInsightsResponseDTO.RiskMetricsDTO(
                        toDouble(riskSummary.sharpeRatio()),
                        toDouble(riskSummary.sortinoRatio()),
                        toDouble(drawdownSummary.maxDrawdown())
                ),
                new StockInsightsResponseDTO.PerformanceDistributionDTO(
                        distributionSummary.positiveDays(),
                        distributionSummary.negativeDays(),
                        distributionSummary.flatDays()
                ),
                new StockInsightsResponseDTO.DrawdownAnalysisDTO(
                        toDouble(drawdownSummary.maxDrawdown()),
                        drawdownSummary.peakDate() == null ? null : drawdownSummary.peakDate().toString(),
                        drawdownSummary.troughDate() == null ? null : drawdownSummary.troughDate().toString()
                ),
                latestNews,
                monthlyReturnsHeatmap,
                historyPoints
        );
    }

    public List<AnalyticsNewsItemDTO> getLatestMarketNews(int limit) {
        int pageSize = Math.max(1, Math.min(limit, 50));
        List<StockMarketData> rows = stockMarketDataRepository.findByDailyNewsIsNotNullOrderByTradingDateDesc(
                PageRequest.of(0, pageSize)
        );
        List<AnalyticsNewsItemDTO> response = new ArrayList<>();
        for (StockMarketData row : rows) {
            if (row.getDailyNews() == null || row.getDailyNews().isBlank() || row.getStock() == null) {
                continue;
            }
            response.add(new AnalyticsNewsItemDTO(
                    row.getStock().getStockId(),
                    row.getStock().getSymbol(),
                    row.getTradingDate() == null ? null : row.getTradingDate().toString(),
                    row.getDailyNews(),
                    toDouble(row.getSentimentScore()),
                    row.getNewsCount()
            ));
        }
        return response;
    }

    private String resolveExchange(Stock stock) {
        if (stock.getExchange() == null) {
            return null;
        }
        if (stock.getExchange().getAcronym() != null && !stock.getExchange().getAcronym().isBlank()) {
            return stock.getExchange().getAcronym();
        }
        return stock.getExchange().getMic();
    }

    private String resolveLastUpdated(AllStocksLastValueCache realtime, StockMarketData latestHistorical) {
        if (realtime != null && realtime.getAggregateUpdatedAt() != null) {
            return realtime.getAggregateUpdatedAt().toString();
        }
        return latestHistorical.getUpdatedAt() == null ? null : latestHistorical.getUpdatedAt().toString();
    }

    // Current price: realtime cache when market is open, latest daily close otherwise
    private BigDecimal resolveCurrentPrice(AllStocksLastValueCache realtime, StockMarketData latestHistorical) {
        if (realtime != null && realtime.getCachedClose() != null) {
            return realtime.getCachedClose();
        }
        return latestHistorical.getClosePrice();
    }

    // Previous close: latest daily OHLC close when realtime is available (intraday vs yesterday),
    // or second-to-last daily OHLC close when market is closed (both current and latest are the same row).
    private BigDecimal resolvePreviousClose(AllStocksLastValueCache realtime, StockMarketData latestHistorical, StockMarketData priorHistorical) {
        boolean hasRealtime = realtime != null && realtime.getCachedClose() != null;
        if (hasRealtime) {
            // Realtime price is intraday — previous close is yesterday's daily close
            return latestHistorical.getClosePrice();
        }
        // No realtime — current price IS the latest daily close, so previous close is the one before it
        return priorHistorical == null ? null : priorHistorical.getClosePrice();
    }

    // Daily change %: prefer the pre-computed change percent from realtime cache (accurate intraday),
    // fall back to computing from current vs previous close.
    private BigDecimal resolveDailyChangePercent(AllStocksLastValueCache realtime, BigDecimal previousClose, BigDecimal currentPrice) {
        if (realtime != null && realtime.getCachedChangePercent() != null) {
            return realtime.getCachedChangePercent();
        }
        return percentChange(previousClose, currentPrice);
    }

    private long resolveLatestTradingDayVolume(StockMarketData latestHistorical) {
        return latestHistorical.getVolume() == null ? 0L : latestHistorical.getVolume();
    }

    private List<StockInsightsResponseDTO.StockHistoryPointDTO> buildHistoryPoints(List<StockMarketData> historyAsc) {
        List<StockInsightsResponseDTO.StockHistoryPointDTO> points = new ArrayList<>(historyAsc.size());
        for (StockMarketData point : historyAsc) {

            points.add(new StockInsightsResponseDTO.StockHistoryPointDTO(
                    point.getTradingDate() == null ? null : point.getTradingDate().toString(),
                    toDouble(point.getOpenPrice()),
                    toDouble(point.getHighPrice()),
                    toDouble(point.getLowPrice()),
                    toDouble(point.getClosePrice()),
                    point.getVolume(),
                    toDouble(point.getSma20()),
                    toDouble(point.getSma50()),
                    toDouble(point.getSma200()),
                    toDouble(point.getVolatility30d()),
                    toDouble(point.getVolatility90d()),
                    toDouble(point.getDailyReturnPercent())
            ));
        }
         return points;
    }

    private List<StockInsightsResponseDTO.DailyNewsDTO> buildLatestNews(List<StockMarketData> historyAsc) {
        List<StockInsightsResponseDTO.DailyNewsDTO> rows = new ArrayList<>();
        for (int i = historyAsc.size() - 1; i >= 0; i--) {
            StockMarketData point = historyAsc.get(i);
            if (point.getDailyNews() == null || point.getDailyNews().isBlank()) {
                continue;
            }
            rows.add(new StockInsightsResponseDTO.DailyNewsDTO(
                    point.getTradingDate() == null ? null : point.getTradingDate().toString(),
                    point.getDailyNews(),
                    toDouble(point.getSentimentScore()),
                    point.getNewsCount()
            ));
            if (rows.size() >= 5) {
                break;
            }
        }
        return rows;
    }

    private List<StockInsightsResponseDTO.MonthlyReturnHeatmapCellDTO> resolveMonthlyReturnsHeatmap(StockMetrics metrics) {
        if (metrics != null && metrics.getMonthlyReturnsHeatmap() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                List<Map<String, Object>> cells = mapper.readValue(
                    metrics.getMonthlyReturnsHeatmap(),
                    new TypeReference<>() {}
                );
                List<StockInsightsResponseDTO.MonthlyReturnHeatmapCellDTO> result = new ArrayList<>();
                for (Map<String, Object> cell : cells) {
                    int year = ((Number) cell.get("year")).intValue();
                    int month = ((Number) cell.get("month")).intValue();
                    Double returnPercent = cell.get("returnPercent") != null
                        ? ((Number) cell.get("returnPercent")).doubleValue()
                        : null;
                    result.add(new StockInsightsResponseDTO.MonthlyReturnHeatmapCellDTO(year, month, returnPercent));
                }
                return result;
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of();
    }

    private DistributionSummary resolveDistributionSummary(StockMetrics metrics) {
        if (metrics != null
                && metrics.getPositiveDays1y() != null
                && metrics.getNegativeDays1y() != null
                && metrics.getFlatDays1y() != null) {
            return new DistributionSummary(
                    metrics.getPositiveDays1y(),
                    metrics.getNegativeDays1y(),
                    metrics.getFlatDays1y());
        }
        return new DistributionSummary(0, 0, 0);
    }

    private DrawdownSummary resolveDrawdownSummary(StockMetrics metrics) {
        if (metrics != null
                && metrics.getMaxDrawdown() != null
                && metrics.getDrawdownPeakDate() != null
                && metrics.getDrawdownTroughDate() != null) {
            return new DrawdownSummary(
                    metrics.getMaxDrawdown(),
                    metrics.getDrawdownPeakDate(),
                    metrics.getDrawdownTroughDate());
        }
        return new DrawdownSummary(null, null, null);
    }

    private BigDecimal percentChange(BigDecimal baseline, BigDecimal current) {
        if (baseline == null || current == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(baseline).divide(baseline, MATH_CONTEXT).multiply(HUNDRED, MATH_CONTEXT);
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private record MacdResult(BigDecimal macd, BigDecimal signal) {
    }

    private record RiskSummary(BigDecimal sharpeRatio, BigDecimal sortinoRatio) {
    }

    private record DistributionSummary(int positiveDays, int negativeDays, int flatDays) {
    }


    private record DrawdownSummary(BigDecimal maxDrawdown, LocalDate peakDate, LocalDate troughDate) {
    }

}


