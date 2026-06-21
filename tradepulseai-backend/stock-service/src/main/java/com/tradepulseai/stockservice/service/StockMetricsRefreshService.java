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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
            StockMetrics metrics = buildMetrics(stockId, historyDesc);
            metricsRows.add(metrics);
        }

        if (metricsRows.isEmpty()) {
            log.info("Stock metrics refresh ({}) found no computable rows.", trigger);
            return;
        }

        stockMetricsRepository.saveAll(metricsRows);
        log.info("Stock metrics refresh ({}) upserted {} rows for latest OHLC date {}.",
                trigger, metricsRows.size(), maxTradingDate.get());
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
        return row;
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

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleNullable(BigDecimal value) {
        return value == null ? null : scale(value);
    }

}
