package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.market.PolygonGroupedDailyMarketSummaryResponse;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PolygonHistoricalBackfillService {

    private static final Logger log = LoggerFactory.getLogger(PolygonHistoricalBackfillService.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private static final String HISTORY_SOURCE = "massive-grouped-daily-history";

    private final StockRepository stockRepository;
    private final StockMarketDataRepository stockMarketDataRepository;
    private final PolygonMarketDataClient polygonMarketDataClient;
    private final int lookbackTradingDays;
    private final long requestDelayMs;
    private final boolean allowRerun;

    public PolygonHistoricalBackfillService(
            StockRepository stockRepository,
            StockMarketDataRepository stockMarketDataRepository,
            PolygonMarketDataClient polygonMarketDataClient,
            @Value("${polygon.backfill.lookback-trading-days:5}") int lookbackTradingDays,
            @Value("${polygon.backfill.request-delay-ms:15000}") long requestDelayMs,
            @Value("${polygon.backfill.allow-rerun:false}") boolean allowRerun
    ) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.polygonMarketDataClient = polygonMarketDataClient;
        this.lookbackTradingDays = lookbackTradingDays;
        this.requestDelayMs = requestDelayMs;
        this.allowRerun = allowRerun;
    }

    @Transactional
    public BackfillResult runTwoYearBackfill() {
        if (!allowRerun && stockMarketDataRepository.existsBySource(HISTORY_SOURCE)) {
            return new BackfillResult(0, 0, 0, 0, "Historical backfill already completed once; rerun is disabled");
        }

        if (!polygonMarketDataClient.isConfigured()) {
            return new BackfillResult(0, 0, 0, 0, "POLYGON_API_KEY is missing");
        }

        List<Stock> stocks = stockRepository.findAll(Sort.by(Sort.Direction.ASC, "stockId"));
        if (stocks.isEmpty()) {
            return new BackfillResult(0, 0, 0, 0, "No stocks found. Bootstrap universe first");
        }

        Map<String, Stock> stocksBySymbol = stocks.stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (left, right) -> left));

        Map<Long, BigDecimal> previousCloseByStockId = new HashMap<>();
        stockMarketDataRepository.findLatestForAllStocks()
                .forEach(existing -> {
                    if (existing.getClosePrice() != null) {
                        previousCloseByStockId.put(existing.getStock().getStockId(), existing.getClosePrice());
                    }
                });

        LocalDate date = LocalDate.now(MARKET_ZONE).minusDays(1);
        int targetTradingDays = Math.max(1, lookbackTradingDays);

        int requestedDays = 0;
        int successfulDays = 0;
        int persistedRows = 0;
        int skippedRows = 0;
        int processedTradingDays = 0;

        while (processedTradingDays < targetTradingDays) {
            if (date.getDayOfWeek().getValue() >= 6) {
                date = date.minusDays(1);
                continue;
            }

            processedTradingDays++;
            requestedDays++;
            Optional<PolygonGroupedDailyMarketSummaryResponse> response = polygonMarketDataClient.fetchGroupedDailyMarketSummary(date);
            if (response.isEmpty()) {
                date = date.minusDays(1);
                continue;
            }

            successfulDays++;
            PolygonGroupedDailyMarketSummaryResponse payload = response.get();
            if (payload.results() == null || payload.results().isEmpty()) {
                date = date.minusDays(1);
                continue;
            }

            for (PolygonGroupedDailyMarketSummaryResponse.PolygonGroupedDailyAggregate aggregate : payload.results()) {
                Stock stock = stocksBySymbol.get(aggregate.ticker());
                if (stock == null) {
                    continue;
                }

                Instant marketTimestamp = aggregate.t() > 0 ? Instant.ofEpochMilli(aggregate.t()) : null;
                if (marketTimestamp == null) {
                    continue;
                }

                if (stockMarketDataRepository.existsByStockAndMarketTimestamp(stock, marketTimestamp)) {
                    skippedRows++;
                    continue;
                }

                BigDecimal closePrice = BigDecimal.valueOf(aggregate.c()).setScale(4, RoundingMode.HALF_UP);
                BigDecimal previousClose = previousCloseByStockId.get(stock.getStockId());

                BigDecimal changePercent = BigDecimal.ZERO;
                if (previousClose != null && previousClose.compareTo(BigDecimal.ZERO) > 0) {
                    changePercent = closePrice.subtract(previousClose)
                            .divide(previousClose, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);
                }

                StockMarketData marketData = new StockMarketData();
                marketData.setStock(stock);
                marketData.setMarketTimestamp(marketTimestamp);
                marketData.setOpenPrice(BigDecimal.valueOf(aggregate.o()).setScale(4, RoundingMode.HALF_UP));
                marketData.setHighPrice(BigDecimal.valueOf(aggregate.h()).setScale(4, RoundingMode.HALF_UP));
                marketData.setLowPrice(BigDecimal.valueOf(aggregate.l()).setScale(4, RoundingMode.HALF_UP));
                marketData.setClosePrice(closePrice);
                marketData.setVolume(Math.round(aggregate.v()));
                marketData.setChangePercent(changePercent);
                marketData.setSource(HISTORY_SOURCE);
                stockMarketDataRepository.save(marketData);

                previousCloseByStockId.put(stock.getStockId(), closePrice);
                persistedRows++;
            }

            date = date.minusDays(1);

            if (requestDelayMs > 0) {
                try {
                    Thread.sleep(requestDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[POLYGON] Historical backfill interrupted");
                    break;
                }
            }
        }

        return new BackfillResult(requestedDays, successfulDays, persistedRows, skippedRows, "Completed");
    }

    public record BackfillResult(
            int requestedDays,
            int successfulDays,
            int persistedRows,
            int skippedRows,
            String message
    ) {
    }
}

