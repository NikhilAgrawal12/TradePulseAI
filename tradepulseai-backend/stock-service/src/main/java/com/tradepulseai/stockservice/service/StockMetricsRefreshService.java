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
    private static final int LOOKBACK_ROWS = 260;
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

        BigDecimal currentPrice = latest.getClosePrice();
        BigDecimal weekReturn = computeReturn(closes, 5);
        BigDecimal monthReturn = computeReturn(closes, 21);
        BigDecimal yearReturn = computeReturn(closes, 252);
        BigDecimal volatility30d = computeVolatilityPercent(closes, 30);
        BigDecimal volatility90d = computeVolatilityPercent(closes, 90);
        BigDecimal avgVolume30d = averageVolume(volumes, 30);
        BigDecimal high52w = maxHigh(historyAsc, 252);
        BigDecimal low52w = minLow(historyAsc, 252);
        BigDecimal rsi14 = computeRsi(closes, 14);
        BigDecimal sma20 = simpleMovingAverage(closes, 20);
        BigDecimal sma50 = simpleMovingAverage(closes, 50);
        BigDecimal sma200 = simpleMovingAverage(closes, 200);

        StockMetrics row = new StockMetrics();
        row.setStockId(stockId);
        row.setCurrentPrice(scale(valueOrZero(currentPrice)));
        row.setWeekReturn(scale(valueOrZero(weekReturn)));
        row.setMonthReturn(scale(valueOrZero(monthReturn)));
        row.setYearReturn(scale(valueOrZero(yearReturn)));
        row.setVolatility30d(scale(valueOrZero(volatility30d)));
        row.setVolatility90d(scale(valueOrZero(volatility90d)));
        row.setAvgVolume30d(scale(valueOrZero(avgVolume30d)));
        row.setHigh52w(scale(valueOrZero(high52w)));
        row.setLow52w(scale(valueOrZero(low52w)));
        row.setRsi14(scale(valueOrZero(rsi14)));
        row.setSma20(scale(valueOrZero(sma20)));
        row.setSma50(scale(valueOrZero(sma50)));
        row.setSma200(scale(valueOrZero(sma200)));
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

    private BigDecimal computeVolatilityPercent(List<BigDecimal> closes, int periods) {
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
        return BigDecimal.valueOf(stdDev * 100.0d);
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

    private BigDecimal simpleMovingAverage(List<BigDecimal> closes, int periods) {
        int size = closes.size();
        if (size < periods) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = size - periods; i < size; i++) {
            BigDecimal close = closes.get(i);
            if (close == null) {
                return null;
            }
            sum = sum.add(close);
        }
        return sum.divide(BigDecimal.valueOf(periods), MATH_CONTEXT);
    }

    private BigDecimal computeRsi(List<BigDecimal> closes, int periods) {
        int size = closes.size();
        if (size < periods + 1) {
            return null;
        }

        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        int start = size - (periods + 1);
        for (int i = start + 1; i < size; i++) {
            BigDecimal prev = closes.get(i - 1);
            BigDecimal current = closes.get(i);
            if (prev == null || current == null) {
                return null;
            }
            BigDecimal delta = current.subtract(prev);
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
        BigDecimal denominator = BigDecimal.ONE.add(rs);
        return BigDecimal.valueOf(100).subtract(BigDecimal.valueOf(100).divide(denominator, MATH_CONTEXT));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
