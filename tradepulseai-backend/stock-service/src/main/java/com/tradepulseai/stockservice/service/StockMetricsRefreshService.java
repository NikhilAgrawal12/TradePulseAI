package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.model.StockMetrics;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import com.tradepulseai.stockservice.repository.StockMetricsRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class StockMetricsRefreshService {

    private static final Logger log = LoggerFactory.getLogger(StockMetricsRefreshService.class);
    private static final int LOOKBACK_ROWS = 800;
    private static final int ONE_WEEK_PERIODS = 5;
    private static final int ONE_MONTH_PERIODS = 21;
    private static final int THREE_MONTH_PERIODS = 63;
    private static final int SIX_MONTH_PERIODS = 126;
    private static final int ONE_YEAR_PERIODS = 252;
    private static final int THREE_YEAR_PERIODS = 756;
    private static final double SQRT_252 = Math.sqrt(252.0d);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final StockRepository stockRepository;
    private final StockMarketDataRepository stockMarketDataRepository;
    private final StockMetricsRepository stockMetricsRepository;

    public StockMetricsRefreshService(
            StockRepository stockRepository,
            StockMarketDataRepository stockMarketDataRepository,
            StockMetricsRepository stockMetricsRepository) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.stockMetricsRepository = stockMetricsRepository;
    }

    @Transactional
    public void refreshAllForLatestOhlc(String trigger) {
        Optional<LocalDate> maxTradingDate = stockMarketDataRepository.findMaxTradingDate();
        if (maxTradingDate.isEmpty()) {
            log.info("Skipping stock metrics refresh ({}) because no OHLC rows exist.", trigger);
            return;
        }

        List<Stock> stocks = stockRepository.findAllByOrderByStockIdAsc();
        if (stocks.isEmpty()) {
            log.info("Skipping stock metrics refresh ({}) because stocks table is empty.", trigger);
            return;
        }

        List<StockMetrics> metricsRows = new ArrayList<>();
        List<StockMarketData> indicatorRows = new ArrayList<>();
        for (Stock stock : stocks) {
            Long stockId = stock.getStockId();
            if (stockId == null) {
                continue;
            }

            List<StockMarketData> historyDesc = stockMarketDataRepository.findRecentByStockId(stockId, PageRequest.of(0, LOOKBACK_ROWS));
            if (historyDesc.isEmpty()) {
                continue;
            }

            historyDesc.sort(Comparator.comparing(StockMarketData::getTradingDate));
            applyIndicatorsToHistory(historyDesc);
            indicatorRows.addAll(historyDesc);
            StockMetrics metrics = buildMetrics(stockId, historyDesc);
            metricsRows.add(metrics);
        }

        if (metricsRows.isEmpty()) {
            log.info("Stock metrics refresh ({}) found no computable rows.", trigger);
            return;
        }

        stockMarketDataRepository.saveAll(indicatorRows);
        stockMetricsRepository.saveAll(metricsRows);
        log.info("Stock metrics refresh ({}) upserted {} rows for latest OHLC date {}.",
                trigger, metricsRows.size(), maxTradingDate.get());
    }

    private void applyIndicatorsToHistory(List<StockMarketData> historyAsc) {
        List<BigDecimal> closes = historyAsc.stream().map(StockMarketData::getClosePrice).toList();

        // Pre-compute RSI and MACD per-row using EMA series over all closes
        List<Double> closeDoubles = closes.stream()
                .map(v -> v == null ? null : v.doubleValue())
                .filter(java.util.Objects::nonNull)
                .toList();
        List<Double> ema12Series = emaSeries(closeDoubles, 12);
        List<Double> ema26Series = emaSeries(closeDoubles, 26);
        List<Double> macdSeries = new ArrayList<>();
        for (int i = 0; i < closeDoubles.size(); i++) {
            Double e12 = ema12Series.get(i);
            Double e26 = ema26Series.get(i);
            macdSeries.add(e12 == null || e26 == null ? null : e12 - e26);
        }
        List<Double> signalSeries = emaSeries(
                macdSeries.stream().filter(java.util.Objects::nonNull).toList(), 9);

        for (int index = 0; index < historyAsc.size(); index++) {
            StockMarketData row = historyAsc.get(index);
            row.setSma20(scaleNullable(simpleMovingAverageAt(closes, index, 20)));
            row.setSma50(scaleNullable(simpleMovingAverageAt(closes, index, 50)));
            row.setSma200(scaleNullable(simpleMovingAverageAt(closes, index, 200)));
            row.setVolatility30d(scaleNullable(computeAnnualizedVolatilityAt(closes, index, 30)));
            row.setVolatility90d(scaleNullable(computeAnnualizedVolatilityAt(closes, index, 90)));

            BigDecimal dailyReturn = null;
            if (index > 0) {
                dailyReturn = percentChange(closes.get(index - 1), closes.get(index));
            }
            row.setDailyReturnPercent(scaleNullable(dailyReturn));

            // 5-day return
            BigDecimal return5d = index >= 5
                    ? percentChange(closes.get(index - 5), closes.get(index)) : null;
            row.setReturn5d(return5d == null ? null : return5d.setScale(4, RoundingMode.HALF_UP));

            // 20-day momentum (same as 20-day return expressed as % change)
            BigDecimal momentum20d = index >= 20
                    ? percentChange(closes.get(index - 20), closes.get(index)) : null;
            row.setMomentum20d(momentum20d == null ? null : momentum20d.setScale(4, RoundingMode.HALF_UP));

            // RSI 14 using a rolling window up to this index
            BigDecimal rsi14 = computeRsi(closes.subList(0, index + 1), 14);
            row.setRsi14(scaleNullable4(rsi14));

            // MACD and signal from pre-computed series
            if (index < macdSeries.size() && macdSeries.get(index) != null) {
                row.setMacd(BigDecimal.valueOf(macdSeries.get(index)).setScale(4, RoundingMode.HALF_UP));
            }
            // Signal aligns with compacted macd series index, approximate by using signalSeries at same position
            long nonNullBefore = macdSeries.subList(0, index + 1).stream().filter(java.util.Objects::nonNull).count();
            if (nonNullBefore > 0 && nonNullBefore - 1 < signalSeries.size()) {
                Double sig = signalSeries.get((int) nonNullBefore - 1);
                if (sig != null) {
                    row.setMacdSignal(BigDecimal.valueOf(sig).setScale(4, RoundingMode.HALF_UP));
                }
            }
        }
    }

    private BigDecimal scaleNullable4(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    private StockMetrics buildMetrics(Long stockId, List<StockMarketData> historyAsc) {
        StockMarketData latest = historyAsc.get(historyAsc.size() - 1);
        List<BigDecimal> closes = historyAsc.stream().map(StockMarketData::getClosePrice).toList();
        List<Long> volumes = historyAsc.stream().map(StockMarketData::getVolume).toList();

        BigDecimal weekReturn = computeReturn(closes, ONE_WEEK_PERIODS);
        BigDecimal monthReturn = computeReturn(closes, ONE_MONTH_PERIODS);
        BigDecimal threeMonthReturn = computeReturn(closes, THREE_MONTH_PERIODS);
        BigDecimal sixMonthReturn = computeReturn(closes, SIX_MONTH_PERIODS);
        BigDecimal yearReturn = computeReturn(closes, ONE_YEAR_PERIODS);
        BigDecimal threeYearReturn = computeReturn(closes, THREE_YEAR_PERIODS);
        BigDecimal high52w = maxHigh(historyAsc, ONE_YEAR_PERIODS);
        BigDecimal low52w = minLow(historyAsc, ONE_YEAR_PERIODS);
        BigDecimal currentPrice = latest.getClosePrice();
        BigDecimal distanceFromHighPercent = percentDistance(currentPrice, high52w);
        BigDecimal distanceFromLowPercent = percentDistance(currentPrice, low52w);
        BigDecimal average30DayVolume = averageVolume(volumes, 30);
        Long latestTradingDayVolume = latest.getVolume();
        BigDecimal relativeVolume = latestTradingDayVolume == null
                || average30DayVolume == null
                || average30DayVolume.compareTo(BigDecimal.ZERO) == 0
                ? null
                : BigDecimal.valueOf(latestTradingDayVolume).divide(average30DayVolume, MATH_CONTEXT);
        BigDecimal volatility30d = computeAnnualizedVolatility(closes, 30);
        BigDecimal volatility90d = computeAnnualizedVolatility(closes, 90);
        int[] distribution = computeDistribution(historyAsc, ONE_YEAR_PERIODS);
        DrawdownSummary drawdownSummary = computeDrawdownSummary(historyAsc);
        RiskSummary riskSummary = computeRiskSummary(closes);
        BigDecimal rsi14 = computeRsi(closes, 14);
        MacdResult macdResult = computeMacd(closes);
        BigDecimal momentum30d = computeReturn(closes, 30);
        BigDecimal sma50 = simpleMovingAverage(closes, 50);
        BigDecimal sma200 = simpleMovingAverage(closes, 200);
        Boolean goldenCross = sma50 != null && sma200 != null ? sma50.compareTo(sma200) > 0 : null;
        Boolean deathCross = sma50 != null && sma200 != null ? sma50.compareTo(sma200) < 0 : null;
        String monthlyReturnsJson = serializeMonthlyReturnsHeatmap(historyAsc);

        StockMetrics row = new StockMetrics();
        row.setStockId(stockId);
        row.setWeekReturn(scaleNullable(weekReturn));
        row.setMonthReturn(scaleNullable(monthReturn));
        row.setThreeMonthReturn(scaleNullable(threeMonthReturn));
        row.setSixMonthReturn(scaleNullable(sixMonthReturn));
        row.setYearReturn(scaleNullable(yearReturn));
        row.setThreeYearReturn(scaleNullable(threeYearReturn));
        row.setHigh52w(scaleNullable(high52w));
        row.setLow52w(scaleNullable(low52w));
        row.setDistanceFromHighPercent(scaleNullable(distanceFromHighPercent));
        row.setDistanceFromLowPercent(scaleNullable(distanceFromLowPercent));
        row.setAvgVolume30d(scaleNullable(average30DayVolume));
        row.setLatestTradingDayVolume(latestTradingDayVolume);
        row.setLatestTradingDate(latest.getTradingDate());
        row.setRelativeVolume(scaleNullable(relativeVolume));
        row.setVolatility30d(scaleNullable(volatility30d));
        row.setVolatility90d(scaleNullable(volatility90d));
        row.setPositiveDays1y(distribution[0]);
        row.setNegativeDays1y(distribution[1]);
        row.setFlatDays1y(distribution[2]);
        row.setMonthlyReturnsHeatmap(monthlyReturnsJson);
        row.setMaxDrawdown(scaleNullable(drawdownSummary.maxDrawdown()));
        row.setDrawdownPeakDate(drawdownSummary.peakDate());
        row.setDrawdownTroughDate(drawdownSummary.troughDate());
        row.setSharpeRatio(scaleNullable(riskSummary.sharpeRatio()));
        row.setSortinoRatio(scaleNullable(riskSummary.sortinoRatio()));
        row.setRsi14(scaleNullable(rsi14));
        row.setMacd(scaleNullable(macdResult.macd()));
        row.setMacdSignal(scaleNullable(macdResult.signal()));
        row.setMomentum30d(scaleNullable(momentum30d));
        row.setGoldenCross(goldenCross);
        row.setDeathCross(deathCross);
        return row;
    }

    /** Returns [positiveDays, negativeDays, flatDays] for up to the last {@code periods} trading rows. */
    private int[] computeDistribution(List<StockMarketData> historyAsc, int periods) {
        int size = historyAsc.size();
        int start = Math.max(1, size - periods);
        int positive = 0, negative = 0, flat = 0;
        for (int i = start; i < size; i++) {
            BigDecimal prev = historyAsc.get(i - 1).getClosePrice();
            BigDecimal curr = historyAsc.get(i).getClosePrice();
            if (prev == null || curr == null) continue;
            int cmp = curr.compareTo(prev);
            if (cmp > 0) positive++;
            else if (cmp < 0) negative++;
            else flat++;
        }
        return new int[]{positive, negative, flat};
    }

    private BigDecimal computeReturn(List<BigDecimal> closes, int lookbackPeriods) {
        int size = closes.size();
        if (size <= lookbackPeriods) {
            return null;
        }

        BigDecimal latest = closes.get(size - 1);
        BigDecimal baseline = closes.get(size - 1 - lookbackPeriods);
        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0 || latest == null) {
            return null;
        }
        return latest.divide(baseline, MATH_CONTEXT).subtract(BigDecimal.ONE).multiply(HUNDRED, MATH_CONTEXT);
    }

    private BigDecimal percentDistance(BigDecimal current, BigDecimal anchor) {
        if (current == null || anchor == null || anchor.compareTo(ZERO) == 0) {
            return null;
        }
        return current.subtract(anchor).divide(anchor, MATH_CONTEXT).multiply(HUNDRED, MATH_CONTEXT);
    }

    private BigDecimal maxHigh(List<StockMarketData> historyAsc, int periods) {
        int size = historyAsc.size();
        int from = Math.max(0, size - periods);
        BigDecimal max = null;
        for (int i = from; i < size; i++) {
            BigDecimal high = historyAsc.get(i).getHighPrice();
            if (high == null) {
                continue;
            }
            if (max == null || high.compareTo(max) > 0) {
                max = high;
            }
        }
        return max;
    }

    private BigDecimal minLow(List<StockMarketData> historyAsc, int periods) {
        int size = historyAsc.size();
        int from = Math.max(0, size - periods);
        BigDecimal min = null;
        for (int i = from; i < size; i++) {
            BigDecimal low = historyAsc.get(i).getLowPrice();
            if (low == null) {
                continue;
            }
            if (min == null || low.compareTo(min) < 0) {
                min = low;
            }
        }
        return min;
    }

    private BigDecimal averageVolume(List<Long> volumes, int periods) {
        if (volumes.isEmpty()) {
            return null;
        }

        int size = volumes.size();
        int from = Math.max(0, size - periods);
        long count = 0L;
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = from; i < size; i++) {
            Long volume = volumes.get(i);
            if (volume == null) {
                continue;
            }
            sum = sum.add(BigDecimal.valueOf(volume));
            count++;
        }
        if (count == 0L) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), MATH_CONTEXT);
    }

    private BigDecimal computeAnnualizedVolatility(List<BigDecimal> closes, int periods) {
        List<Double> returns = dailyReturns(closes, periods);
        if (returns.size() < 2) {
            return null;
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double sumSquaredDiff = 0.0d;
        for (double value : returns) {
            double delta = value - mean;
            sumSquaredDiff += delta * delta;
        }

        double variance = sumSquaredDiff / (returns.size() - 1);
        double stdDev = Math.sqrt(variance);
        return BigDecimal.valueOf(stdDev * SQRT_252 * 100.0d);
    }

    private BigDecimal computeAnnualizedVolatilityAt(List<BigDecimal> closes, int inclusiveIndex, int periods) {
        if (inclusiveIndex < periods) {
            return null;
        }
        List<BigDecimal> subset = closes.subList(0, inclusiveIndex + 1);
        return computeAnnualizedVolatility(subset, periods);
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

    private List<Double> dailyReturns(List<BigDecimal> closes, int periods) {
        int size = closes.size();
        if (size < periods + 1) {
            return List.of();
        }

        int start = size - (periods + 1);
        List<Double> values = new ArrayList<>(periods);
        for (int i = start + 1; i < size; i++) {
            BigDecimal prev = closes.get(i - 1);
            BigDecimal current = closes.get(i);
            if (prev == null || current == null || prev.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            double ret = current.subtract(prev).divide(prev, MATH_CONTEXT).doubleValue();
            values.add(ret);
        }
        return values;
    }

    private RiskSummary computeRiskSummary(List<BigDecimal> closes) {
        List<Double> returns = dailyReturns(closes, ONE_YEAR_PERIODS);
        if (returns.size() < 2) {
            return new RiskSummary(null, null);
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        double stdDev = standardDeviation(returns, mean);
        BigDecimal sharpe = stdDev == 0.0d ? null : BigDecimal.valueOf((mean / stdDev) * SQRT_252);

        List<Double> downside = returns.stream().filter(value -> value < 0.0d).toList();
        BigDecimal sortino = null;
        if (!downside.isEmpty()) {
            double downsideMean = downside.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
            double downsideStdDev = standardDeviation(downside, downsideMean);
            if (downsideStdDev != 0.0d) {
                sortino = BigDecimal.valueOf((mean / downsideStdDev) * SQRT_252);
            }
        }

        return new RiskSummary(sharpe, sortino);
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
        if (closes.size() < periods) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int start = closes.size() - periods;
        for (int index = start; index < closes.size(); index++) {
            BigDecimal close = closes.get(index);
            if (close == null) {
                return null;
            }
            sum = sum.add(close);
        }
        return sum.divide(BigDecimal.valueOf(periods), MATH_CONTEXT);
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
        return new MacdResult(BigDecimal.valueOf(macd), signal == null ? null : BigDecimal.valueOf(signal));
    }

    private List<Double> emaSeries(List<Double> values, int periods) {
        List<Double> ema = new ArrayList<>(values.size());
        double multiplier = 2.0d / (periods + 1.0d);
        Double previous = null;
        for (Double value : values) {
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

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleNullable(BigDecimal value) {
        return value == null ? null : scale(value);
    }

    /** Computes and serializes monthly returns heatmap as JSON. */
    private String serializeMonthlyReturnsHeatmap(List<StockMarketData> historyAsc) {
        try {
            Map<YearMonth, BigDecimal> monthCloseMap = new LinkedHashMap<>();
            for (StockMarketData point : historyAsc) {
                if (point.getTradingDate() == null || point.getClosePrice() == null) {
                    continue;
                }
                monthCloseMap.put(YearMonth.from(point.getTradingDate()), point.getClosePrice());
            }

            List<Map.Entry<YearMonth, BigDecimal>> monthlyEntries = new ArrayList<>(monthCloseMap.entrySet());
            List<Map<String, Object>> cells = new ArrayList<>();
            for (int index = 1; index < monthlyEntries.size(); index++) {
                Map.Entry<YearMonth, BigDecimal> current = monthlyEntries.get(index);
                Map.Entry<YearMonth, BigDecimal> previous = monthlyEntries.get(index - 1);
                BigDecimal monthlyReturn = percentChange(previous.getValue(), current.getValue());

                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("year", current.getKey().getYear());
                cell.put("month", current.getKey().getMonthValue());
                cell.put("returnPercent", monthlyReturn == null ? null : monthlyReturn.setScale(2, RoundingMode.HALF_UP).doubleValue());
                cells.add(cell);
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(cells);
        } catch (Exception e) {
            log.warn("Failed to serialize monthly returns heatmap: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal percentChange(BigDecimal baseline, BigDecimal current) {
        if (baseline == null || current == null || baseline.compareTo(ZERO) == 0) {
            return null;
        }
        return current.subtract(baseline).divide(baseline, MATH_CONTEXT).multiply(BigDecimal.valueOf(100), MATH_CONTEXT);
    }

    private record DrawdownSummary(BigDecimal maxDrawdown, LocalDate peakDate, LocalDate troughDate) {
    }

    private record RiskSummary(BigDecimal sharpeRatio, BigDecimal sortinoRatio) {
    }

    private record MacdResult(BigDecimal macd, BigDecimal signal) {
    }

}
