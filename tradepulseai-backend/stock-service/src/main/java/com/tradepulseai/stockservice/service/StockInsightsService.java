package com.tradepulseai.stockservice.service;

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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class StockInsightsService {

    private static final int HISTORY_LOOKBACK_ROWS = 800;
    private static final int ONE_WEEK_PERIODS = 5;
    private static final int ONE_MONTH_PERIODS = 21;
    private static final int THREE_MONTH_PERIODS = 63;
    private static final int SIX_MONTH_PERIODS = 126;
    private static final int ONE_YEAR_PERIODS = 252;
    private static final int THREE_YEAR_PERIODS = 756;
    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final double SQRT_252 = Math.sqrt(252.0d);

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

        StockMarketData latestHistorical = historyAsc.get(historyAsc.size() - 1);
        StockMarketData priorHistorical = historyAsc.size() > 1 ? historyAsc.get(historyAsc.size() - 2) : null;
        Optional<StockMetrics> metricsOptional = stockMetricsRepository.findById(stockId);
        StockMetrics metrics = metricsOptional.orElse(null);
        AllStocksLastValueCache realtime = allStocksLastValueCacheService.getCacheEntryByStockId(stockId);

        List<BigDecimal> closes = historyAsc.stream().map(StockMarketData::getClosePrice).toList();
        List<Long> volumes = historyAsc.stream().map(StockMarketData::getVolume).toList();
        BigDecimal currentPrice = resolveCurrentPrice(realtime, latestHistorical, metrics);
        BigDecimal previousClose = resolvePreviousClose(currentPrice, latestHistorical, priorHistorical);
        BigDecimal dailyChange = currentPrice != null && previousClose != null ? currentPrice.subtract(previousClose) : null;
        BigDecimal dailyChangePercent = percentChange(previousClose, currentPrice);

        BigDecimal high52Week = coalesce(metrics == null ? null : metrics.getHigh52w(), maxHigh(historyAsc, ONE_YEAR_PERIODS));
        BigDecimal low52Week = coalesce(metrics == null ? null : metrics.getLow52w(), minLow(historyAsc, ONE_YEAR_PERIODS));

        BigDecimal distanceFromHighPercent = high52Week == null || high52Week.compareTo(BigDecimal.ZERO) == 0 || currentPrice == null
                ? null
                : currentPrice.subtract(high52Week).divide(high52Week, MATH_CONTEXT).multiply(HUNDRED, MATH_CONTEXT);
        BigDecimal distanceFromLowPercent = low52Week == null || low52Week.compareTo(BigDecimal.ZERO) == 0 || currentPrice == null
                ? null
                : currentPrice.subtract(low52Week).divide(low52Week, MATH_CONTEXT).multiply(HUNDRED, MATH_CONTEXT);

        BigDecimal oneWeekReturn = coalesce(metrics == null ? null : metrics.getWeekReturn(), computeReturnFromCurrent(currentPrice, closes, ONE_WEEK_PERIODS));
        BigDecimal oneMonthReturn = coalesce(metrics == null ? null : metrics.getMonthReturn(), computeReturnFromCurrent(currentPrice, closes, ONE_MONTH_PERIODS));
        BigDecimal threeMonthReturn = computeReturnFromCurrent(currentPrice, closes, THREE_MONTH_PERIODS);
        BigDecimal sixMonthReturn = computeReturnFromCurrent(currentPrice, closes, SIX_MONTH_PERIODS);
        BigDecimal oneYearReturn = coalesce(metrics == null ? null : metrics.getYearReturn(), computeReturnFromCurrent(currentPrice, closes, ONE_YEAR_PERIODS));
        BigDecimal threeYearReturn = computeReturnFromCurrent(currentPrice, closes, THREE_YEAR_PERIODS);

        long todaysVolume = resolveTodayVolume(latestHistorical);
        BigDecimal average30DayVolume = coalesce(metrics == null ? null : metrics.getAvgVolume30d(), averageVolume(volumes, 30));
        BigDecimal relativeVolume = average30DayVolume == null || average30DayVolume.compareTo(BigDecimal.ZERO) == 0
                ? null
                : BigDecimal.valueOf(todaysVolume).divide(average30DayVolume, MATH_CONTEXT);

        BigDecimal volatility30Day = coalesce(metrics == null ? null : metrics.getVolatility30d(), computeAnnualizedVolatility(closes, 30));
        BigDecimal volatility90Day = coalesce(metrics == null ? null : metrics.getVolatility90d(), computeAnnualizedVolatility(closes, 90));
        BigDecimal volatility1Year = computeAnnualizedVolatility(closes, ONE_YEAR_PERIODS);

        BigDecimal sma20 = coalesce(metrics == null ? null : metrics.getSma20(), simpleMovingAverage(closes, 20));
        BigDecimal sma50 = coalesce(metrics == null ? null : metrics.getSma50(), simpleMovingAverage(closes, 50));
        BigDecimal sma200 = coalesce(metrics == null ? null : metrics.getSma200(), simpleMovingAverage(closes, 200));
        Boolean goldenCross = sma50 != null && sma200 != null ? sma50.compareTo(sma200) >= 0 : null;
        Boolean deathCross = sma50 != null && sma200 != null ? sma50.compareTo(sma200) < 0 : null;

        BigDecimal rsi14 = coalesce(metrics == null ? null : metrics.getRsi14(), computeRsi(closes, 14));
        MacdResult macdResult = computeMacd(closes);
        BigDecimal momentum30Day = computeReturnFromCurrent(currentPrice, closes, 30);
        RiskSummary riskSummary = computeRiskSummary(closes);
        DrawdownSummary drawdownSummary = computeDrawdownSummary(historyAsc);
        BestWorstSummary bestWorstSummary = computeBestWorstSummary(historyAsc);
        DistributionSummary distributionSummary = computeDistributionSummary(historyAsc);
        VolumeSummary volumeSummary = computeVolumeSummary(volumes);
        List<StockInsightsResponseDTO.MonthlyReturnHeatmapCellDTO> monthlyReturnsHeatmap = computeMonthlyReturnsHeatmap(historyAsc);
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
                        todaysVolume,
                        toDouble(average30DayVolume),
                        toDouble(relativeVolume)
                ),
                new StockInsightsResponseDTO.VolatilityMetricsDTO(
                        toDouble(volatility30Day),
                        toDouble(volatility90Day),
                        toDouble(volatility1Year)
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
                        toDouble(drawdownSummary.maxDrawdown()),
                        toDouble(computeBetaVsSp500(stock.getStockId(), historyAsc))
                ),
                new StockInsightsResponseDTO.PerformanceDistributionDTO(
                        distributionSummary.positiveDays(),
                        distributionSummary.negativeDays(),
                        distributionSummary.flatDays()
                ),
                new StockInsightsResponseDTO.VolumeDistributionDTO(
                        volumeSummary.minVolume(),
                        toDouble(volumeSummary.averageVolume()),
                        volumeSummary.maxVolume()
                ),
                new StockInsightsResponseDTO.DrawdownAnalysisDTO(
                        toDouble(drawdownSummary.maxDrawdown()),
                        drawdownSummary.peakDate() == null ? null : drawdownSummary.peakDate().toString(),
                        drawdownSummary.troughDate() == null ? null : drawdownSummary.troughDate().toString()
                ),
                new StockInsightsResponseDTO.BestWorstDaysDTO(
                        toDouble(bestWorstSummary.bestGain()),
                        bestWorstSummary.bestGainDate() == null ? null : bestWorstSummary.bestGainDate().toString(),
                        toDouble(bestWorstSummary.worstLoss()),
                        bestWorstSummary.worstLossDate() == null ? null : bestWorstSummary.worstLossDate().toString()
                ),
                monthlyReturnsHeatmap,
                historyPoints
        );
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

    private BigDecimal resolveCurrentPrice(AllStocksLastValueCache realtime, StockMarketData latestHistorical, StockMetrics metrics) {
        if (realtime != null && realtime.getCachedClose() != null) {
            return realtime.getCachedClose();
        }
        if (latestHistorical.getClosePrice() != null) {
            return latestHistorical.getClosePrice();
        }
        return metrics == null ? null : metrics.getCurrentPrice();
    }

    private BigDecimal resolvePreviousClose(BigDecimal currentPrice, StockMarketData latestHistorical, StockMarketData priorHistorical) {
        BigDecimal latestHistoricalClose = latestHistorical.getClosePrice();
        if (currentPrice == null) {
            return latestHistoricalClose;
        }
        if (latestHistoricalClose == null) {
            return priorHistorical == null ? null : priorHistorical.getClosePrice();
        }
        if (currentPrice.compareTo(latestHistoricalClose) == 0) {
            return priorHistorical == null ? latestHistoricalClose : priorHistorical.getClosePrice();
        }
        return latestHistoricalClose;
    }

    private long resolveTodayVolume(StockMarketData latestHistorical) {
        return latestHistorical.getVolume() == null ? 0L : latestHistorical.getVolume();
    }

    private List<StockInsightsResponseDTO.StockHistoryPointDTO> buildHistoryPoints(List<StockMarketData> historyAsc) {
        List<BigDecimal> closes = historyAsc.stream().map(StockMarketData::getClosePrice).toList();
        List<StockInsightsResponseDTO.StockHistoryPointDTO> points = new ArrayList<>(historyAsc.size());
        for (int index = 0; index < historyAsc.size(); index++) {
            StockMarketData point = historyAsc.get(index);
            BigDecimal sma20 = simpleMovingAverageAt(closes, index, 20);
            BigDecimal sma50 = simpleMovingAverageAt(closes, index, 50);
            BigDecimal sma200 = simpleMovingAverageAt(closes, index, 200);
            BigDecimal volatility30 = computeAnnualizedVolatilityAt(closes, index, 30);
            BigDecimal volatility90 = computeAnnualizedVolatilityAt(closes, index, 90);
            BigDecimal volatility1Year = computeAnnualizedVolatilityAt(closes, index, ONE_YEAR_PERIODS);
            BigDecimal dailyReturn = index == 0 ? null : percentChange(closes.get(index - 1), closes.get(index));

            points.add(new StockInsightsResponseDTO.StockHistoryPointDTO(
                    point.getTradingDate() == null ? null : point.getTradingDate().toString(),
                    toDouble(point.getOpenPrice()),
                    toDouble(point.getHighPrice()),
                    toDouble(point.getLowPrice()),
                    toDouble(point.getClosePrice()),
                    point.getVolume(),
                    toDouble(sma20),
                    toDouble(sma50),
                    toDouble(sma200),
                    toDouble(volatility30),
                    toDouble(volatility90),
                    toDouble(volatility1Year),
                    toDouble(dailyReturn)
            ));
        }
        return points;
    }

    private List<StockInsightsResponseDTO.MonthlyReturnHeatmapCellDTO> computeMonthlyReturnsHeatmap(List<StockMarketData> historyAsc) {
        Map<YearMonth, BigDecimal> monthCloseMap = new LinkedHashMap<>();
        for (StockMarketData point : historyAsc) {
            if (point.getTradingDate() == null || point.getClosePrice() == null) {
                continue;
            }
            monthCloseMap.put(YearMonth.from(point.getTradingDate()), point.getClosePrice());
        }

        List<Map.Entry<YearMonth, BigDecimal>> monthlyEntries = new ArrayList<>(monthCloseMap.entrySet());
        List<StockInsightsResponseDTO.MonthlyReturnHeatmapCellDTO> cells = new ArrayList<>();
        for (int index = 1; index < monthlyEntries.size(); index++) {
            Map.Entry<YearMonth, BigDecimal> current = monthlyEntries.get(index);
            Map.Entry<YearMonth, BigDecimal> previous = monthlyEntries.get(index - 1);
            BigDecimal monthlyReturn = percentChange(previous.getValue(), current.getValue());
            cells.add(new StockInsightsResponseDTO.MonthlyReturnHeatmapCellDTO(
                    current.getKey().getYear(),
                    current.getKey().getMonthValue(),
                    toDouble(monthlyReturn)
            ));
        }
        return cells;
    }

    private DistributionSummary computeDistributionSummary(List<StockMarketData> historyAsc) {
        int positiveDays = 0;
        int negativeDays = 0;
        int flatDays = 0;
        for (int index = 1; index < historyAsc.size(); index++) {
            BigDecimal previous = historyAsc.get(index - 1).getClosePrice();
            BigDecimal current = historyAsc.get(index).getClosePrice();
            if (previous == null || current == null) {
                continue;
            }
            int comparison = current.compareTo(previous);
            if (comparison > 0) {
                positiveDays++;
            } else if (comparison < 0) {
                negativeDays++;
            } else {
                flatDays++;
            }
        }
        return new DistributionSummary(positiveDays, negativeDays, flatDays);
    }

    private VolumeSummary computeVolumeSummary(List<Long> volumes) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        BigDecimal sum = BigDecimal.ZERO;
        long count = 0L;
        for (Long volume : volumes) {
            if (volume == null) {
                continue;
            }
            min = Math.min(min, volume);
            max = Math.max(max, volume);
            sum = sum.add(BigDecimal.valueOf(volume));
            count++;
        }
        if (count == 0L) {
            return new VolumeSummary(null, null, null);
        }
        return new VolumeSummary(min, sum.divide(BigDecimal.valueOf(count), MATH_CONTEXT), max);
    }

    private DrawdownSummary computeDrawdownSummary(List<StockMarketData> historyAsc) {
        BigDecimal runningPeak = null;
        LocalDate runningPeakDate = null;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        LocalDate peakDate = null;
        LocalDate troughDate = null;

        for (StockMarketData point : historyAsc) {
            if (point.getClosePrice() == null || point.getTradingDate() == null) {
                continue;
            }

            if (runningPeak == null || point.getClosePrice().compareTo(runningPeak) > 0) {
                runningPeak = point.getClosePrice();
                runningPeakDate = point.getTradingDate();
                continue;
            }

            BigDecimal drawdown = point.getClosePrice()
                    .subtract(runningPeak)
                    .divide(runningPeak, MATH_CONTEXT)
                    .multiply(HUNDRED, MATH_CONTEXT);
            if (drawdown.compareTo(maxDrawdown) < 0) {
                maxDrawdown = drawdown;
                peakDate = runningPeakDate;
                troughDate = point.getTradingDate();
            }
        }

        return new DrawdownSummary(maxDrawdown, peakDate, troughDate);
    }

    private BestWorstSummary computeBestWorstSummary(List<StockMarketData> historyAsc) {
        BigDecimal bestGain = null;
        BigDecimal worstLoss = null;
        LocalDate bestDate = null;
        LocalDate worstDate = null;

        for (int index = 1; index < historyAsc.size(); index++) {
            BigDecimal previous = historyAsc.get(index - 1).getClosePrice();
            BigDecimal current = historyAsc.get(index).getClosePrice();
            LocalDate tradingDate = historyAsc.get(index).getTradingDate();
            BigDecimal dailyReturn = percentChange(previous, current);
            if (dailyReturn == null || tradingDate == null) {
                continue;
            }
            if (bestGain == null || dailyReturn.compareTo(bestGain) > 0) {
                bestGain = dailyReturn;
                bestDate = tradingDate;
            }
            if (worstLoss == null || dailyReturn.compareTo(worstLoss) < 0) {
                worstLoss = dailyReturn;
                worstDate = tradingDate;
            }
        }

        return new BestWorstSummary(bestGain, bestDate, worstLoss, worstDate);
    }

    private RiskSummary computeRiskSummary(List<BigDecimal> closes) {
        List<Double> returns = dailyReturns(closes, ONE_YEAR_PERIODS);
        if (returns.size() < 2) {
            return new RiskSummary(null, null);
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double stdDev = standardDeviation(returns, mean);
        Double sharpeRatio = stdDev == 0.0d ? null : (mean / stdDev) * SQRT_252;

        List<Double> downside = returns.stream().filter(value -> value < 0.0d).toList();
        Double sortinoRatio = null;
        if (!downside.isEmpty()) {
            double downsideMean = downside.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
            double downsideStdDev = standardDeviation(downside, downsideMean);
            if (downsideStdDev != 0.0d) {
                sortinoRatio = (mean / downsideStdDev) * SQRT_252;
            }
        }

        return new RiskSummary(toBigDecimal(sharpeRatio), toBigDecimal(sortinoRatio));
    }

    private BigDecimal computeBetaVsSp500(Long stockId, List<StockMarketData> stockHistoryAsc) {
        Optional<Stock> benchmarkOptional = stockRepository.findBySymbol("SPY");
        if (benchmarkOptional.isEmpty() || benchmarkOptional.get().getStockId() == null || benchmarkOptional.get().getStockId().equals(stockId)) {
            return null;
        }

        List<StockMarketData> benchmarkHistoryAsc = stockMarketDataRepository
                .findRecentByStockId(benchmarkOptional.get().getStockId(), PageRequest.of(0, HISTORY_LOOKBACK_ROWS));
        if (benchmarkHistoryAsc.isEmpty()) {
            return null;
        }
        benchmarkHistoryAsc = new ArrayList<>(benchmarkHistoryAsc);
        benchmarkHistoryAsc.sort(Comparator.comparing(StockMarketData::getTradingDate));

        Map<LocalDate, Double> stockReturnsByDate = dailyReturnsByDate(stockHistoryAsc, ONE_YEAR_PERIODS);
        Map<LocalDate, Double> benchmarkReturnsByDate = dailyReturnsByDate(benchmarkHistoryAsc, ONE_YEAR_PERIODS);
        List<Double> stockReturns = new ArrayList<>();
        List<Double> benchmarkReturns = new ArrayList<>();
        for (Map.Entry<LocalDate, Double> entry : stockReturnsByDate.entrySet()) {
            Double benchmarkReturn = benchmarkReturnsByDate.get(entry.getKey());
            if (benchmarkReturn == null) {
                continue;
            }
            stockReturns.add(entry.getValue());
            benchmarkReturns.add(benchmarkReturn);
        }
        if (stockReturns.size() < 2) {
            return null;
        }

        double stockMean = stockReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double benchmarkMean = benchmarkReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double covariance = 0.0d;
        double benchmarkVariance = 0.0d;
        for (int index = 0; index < stockReturns.size(); index++) {
            double stockValue = stockReturns.get(index) - stockMean;
            double benchmarkValue = benchmarkReturns.get(index) - benchmarkMean;
            covariance += stockValue * benchmarkValue;
            benchmarkVariance += benchmarkValue * benchmarkValue;
        }
        covariance /= (stockReturns.size() - 1);
        benchmarkVariance /= (benchmarkReturns.size() - 1);
        if (benchmarkVariance == 0.0d) {
            return null;
        }
        return toBigDecimal(covariance / benchmarkVariance);
    }

    private Map<LocalDate, Double> dailyReturnsByDate(List<StockMarketData> historyAsc, int periods) {
        Map<LocalDate, Double> returnsByDate = new LinkedHashMap<>();
        int start = Math.max(1, historyAsc.size() - periods);
        for (int index = start; index < historyAsc.size(); index++) {
            StockMarketData previous = historyAsc.get(index - 1);
            StockMarketData current = historyAsc.get(index);
            if (previous.getTradingDate() == null || previous.getClosePrice() == null || current.getClosePrice() == null) {
                continue;
            }
            Double value = percentChange(previous.getClosePrice(), current.getClosePrice()) == null
                    ? null
                    : percentChange(previous.getClosePrice(), current.getClosePrice()).divide(HUNDRED, MATH_CONTEXT).doubleValue();
            if (value != null && current.getTradingDate() != null) {
                returnsByDate.put(current.getTradingDate(), value);
            }
        }
        return returnsByDate;
    }

    private MacdResult computeMacd(List<BigDecimal> closes) {
        if (closes.size() < 26) {
            return new MacdResult(null, null);
        }
        List<Double> closeValues = closes.stream()
                .map(value -> value == null ? null : value.doubleValue())
                .filter(java.util.Objects::nonNull)
                .toList();
        if (closeValues.size() < 26) {
            return new MacdResult(null, null);
        }

        List<Double> ema12 = emaSeries(closeValues, 12);
        List<Double> ema26 = emaSeries(closeValues, 26);
        List<Double> macdSeries = new ArrayList<>();
        for (int index = 0; index < closeValues.size(); index++) {
            Double shortValue = ema12.get(index);
            Double longValue = ema26.get(index);
            macdSeries.add(shortValue == null || longValue == null ? null : shortValue - longValue);
        }

        List<Double> compactMacd = macdSeries.stream().filter(java.util.Objects::nonNull).toList();
        if (compactMacd.isEmpty()) {
            return new MacdResult(null, null);
        }
        List<Double> signalSeries = emaSeries(compactMacd, 9);
        Double macd = compactMacd.get(compactMacd.size() - 1);
        Double signal = signalSeries.get(signalSeries.size() - 1);
        return new MacdResult(toBigDecimal(macd), toBigDecimal(signal));
    }

    private List<Double> emaSeries(List<Double> values, int periods) {
        List<Double> ema = new ArrayList<>(values.size());
        double multiplier = 2.0d / (periods + 1.0d);
        Double previous = null;
        for (int index = 0; index < values.size(); index++) {
            Double value = values.get(index);
            if (value == null) {
                ema.add(null);
                continue;
            }
            if (previous == null) {
                previous = value;
            } else {
                previous = ((value - previous) * multiplier) + previous;
            }
            ema.add(previous);
        }
        return ema;
    }

    private BigDecimal computeReturnFromCurrent(BigDecimal currentPrice, List<BigDecimal> closes, int lookbackPeriods) {
        if (currentPrice == null || closes.size() <= lookbackPeriods) {
            return null;
        }
        BigDecimal baseline = closes.get(closes.size() - 1 - lookbackPeriods);
        return percentChange(baseline, currentPrice);
    }

    private BigDecimal computeAnnualizedVolatility(List<BigDecimal> closes, int periods) {
        List<Double> returns = dailyReturns(closes, periods);
        if (returns.size() < 2) {
            return null;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double stdDev = standardDeviation(returns, mean);
        return toBigDecimal(stdDev * SQRT_252 * 100.0d);
    }

    private BigDecimal computeAnnualizedVolatilityAt(List<BigDecimal> closes, int inclusiveIndex, int periods) {
        if (inclusiveIndex < periods) {
            return null;
        }
        List<BigDecimal> subset = closes.subList(0, inclusiveIndex + 1);
        return computeAnnualizedVolatility(subset, periods);
    }

    private List<Double> dailyReturns(List<BigDecimal> closes, int periods) {
        int size = closes.size();
        if (size < periods + 1) {
            return List.of();
        }
        int start = size - (periods + 1);
        List<Double> returns = new ArrayList<>(periods);
        for (int index = start + 1; index < size; index++) {
            BigDecimal previous = closes.get(index - 1);
            BigDecimal current = closes.get(index);
            if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            returns.add(current.subtract(previous).divide(previous, MATH_CONTEXT).doubleValue());
        }
        return returns;
    }

    private double standardDeviation(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0.0d;
        }
        double squaredDiff = 0.0d;
        for (double value : values) {
            double delta = value - mean;
            squaredDiff += delta * delta;
        }
        return Math.sqrt(squaredDiff / (values.size() - 1));
    }

    private BigDecimal simpleMovingAverage(List<BigDecimal> closes, int periods) {
        return simpleMovingAverageAt(closes, closes.size() - 1, periods);
    }

    private BigDecimal simpleMovingAverageAt(List<BigDecimal> closes, int inclusiveIndex, int periods) {
        if (inclusiveIndex + 1 < periods) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = inclusiveIndex - periods + 1; index <= inclusiveIndex; index++) {
            BigDecimal close = closes.get(index);
            if (close == null) {
                return null;
            }
            sum = sum.add(close);
        }
        return sum.divide(BigDecimal.valueOf(periods), MATH_CONTEXT);
    }

    private BigDecimal averageVolume(List<Long> volumes, int periods) {
        if (volumes.isEmpty()) {
            return null;
        }
        int from = Math.max(0, volumes.size() - periods);
        BigDecimal sum = BigDecimal.ZERO;
        long count = 0L;
        for (int index = from; index < volumes.size(); index++) {
            Long volume = volumes.get(index);
            if (volume == null) {
                continue;
            }
            sum = sum.add(BigDecimal.valueOf(volume));
            count++;
        }
        return count == 0L ? null : sum.divide(BigDecimal.valueOf(count), MATH_CONTEXT);
    }

    private BigDecimal maxHigh(List<StockMarketData> historyAsc, int periods) {
        int from = Math.max(0, historyAsc.size() - periods);
        BigDecimal max = null;
        for (int index = from; index < historyAsc.size(); index++) {
            BigDecimal value = historyAsc.get(index).getHighPrice();
            if (value == null) {
                continue;
            }
            if (max == null || value.compareTo(max) > 0) {
                max = value;
            }
        }
        return max;
    }

    private BigDecimal minLow(List<StockMarketData> historyAsc, int periods) {
        int from = Math.max(0, historyAsc.size() - periods);
        BigDecimal min = null;
        for (int index = from; index < historyAsc.size(); index++) {
            BigDecimal value = historyAsc.get(index).getLowPrice();
            if (value == null) {
                continue;
            }
            if (min == null || value.compareTo(min) < 0) {
                min = value;
            }
        }
        return min;
    }

    private BigDecimal computeRsi(List<BigDecimal> closes, int periods) {
        if (closes.size() < periods + 1) {
            return null;
        }
        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        int start = closes.size() - (periods + 1);
        for (int index = start + 1; index < closes.size(); index++) {
            BigDecimal previous = closes.get(index - 1);
            BigDecimal current = closes.get(index);
            if (previous == null || current == null) {
                return null;
            }
            BigDecimal delta = current.subtract(previous);
            if (delta.compareTo(BigDecimal.ZERO) >= 0) {
                gainSum = gainSum.add(delta);
            } else {
                lossSum = lossSum.add(delta.abs());
            }
        }
        BigDecimal averageGain = gainSum.divide(BigDecimal.valueOf(periods), MATH_CONTEXT);
        BigDecimal averageLoss = lossSum.divide(BigDecimal.valueOf(periods), MATH_CONTEXT);
        if (averageLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }
        BigDecimal rs = averageGain.divide(averageLoss, MATH_CONTEXT);
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), MATH_CONTEXT));
    }

    private BigDecimal percentChange(BigDecimal baseline, BigDecimal current) {
        if (baseline == null || current == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(baseline).divide(baseline, MATH_CONTEXT).multiply(HUNDRED, MATH_CONTEXT);
    }

    private BigDecimal coalesce(BigDecimal primary, BigDecimal fallback) {
        return primary != null ? primary : fallback;
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private record MacdResult(BigDecimal macd, BigDecimal signal) {
    }

    private record RiskSummary(BigDecimal sharpeRatio, BigDecimal sortinoRatio) {
    }

    private record DistributionSummary(int positiveDays, int negativeDays, int flatDays) {
    }

    private record VolumeSummary(Long minVolume, BigDecimal averageVolume, Long maxVolume) {
    }

    private record DrawdownSummary(BigDecimal maxDrawdown, LocalDate peakDate, LocalDate troughDate) {
    }

    private record BestWorstSummary(BigDecimal bestGain, LocalDate bestGainDate, BigDecimal worstLoss, LocalDate worstLossDate) {
    }
}


