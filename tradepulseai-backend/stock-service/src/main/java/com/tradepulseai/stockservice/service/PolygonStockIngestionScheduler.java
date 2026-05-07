package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.market.PolygonGroupedDailyMarketSummaryResponse;
import com.tradepulseai.stockservice.dto.stock.StockResponseDTO;
import com.tradepulseai.stockservice.mapper.StockMapper;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PolygonStockIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolygonStockIngestionScheduler.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    private final StockRepository stockRepository;
    private final StockMarketDataRepository stockMarketDataRepository;
    private final PolygonMarketDataClient polygonMarketDataClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final boolean fetchEnabled;

    private final AtomicBoolean missingKeyLogged = new AtomicBoolean(false);

    public PolygonStockIngestionScheduler(
            StockRepository stockRepository,
            StockMarketDataRepository stockMarketDataRepository,
            PolygonMarketDataClient polygonMarketDataClient,
            SimpMessagingTemplate messagingTemplate,
            @Value("${polygon.fetch.enabled:false}") boolean fetchEnabled
    ) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.polygonMarketDataClient = polygonMarketDataClient;
        this.messagingTemplate = messagingTemplate;
        this.fetchEnabled = fetchEnabled;
    }

    @Transactional
    @Scheduled(cron = "${polygon.fetch.cron:0 */15 17-23 * * MON-FRI}", zone = "America/New_York")
    public void fetchAndPersistDaily() {
        if (!fetchEnabled) {
            return;
        }

        if (!polygonMarketDataClient.isConfigured()) {
            if (missingKeyLogged.compareAndSet(false, true)) {
                log.warn("[POLYGON] ⚠️  Polygon ingestion enabled but POLYGON_API_KEY is missing. Using mock data only.");
            }
            return;
        }

        List<Stock> stocks = stockRepository.findAll(Sort.by(Sort.Direction.ASC, "stockId"));
        if (stocks.isEmpty()) {
            log.debug("[POLYGON] No stocks available for Polygon ingestion.");
            return;
        }

        LocalDate marketDate = LocalDate.now(MARKET_ZONE);
        log.info("[POLYGON] Polling grouped daily summary for market date {}", marketDate);

        polygonMarketDataClient.fetchGroupedDailyMarketSummary(marketDate)
                .ifPresent(response -> persistGroupedSummary(stocks, response));
    }

    private void persistGroupedSummary(
            List<Stock> stocks,
            PolygonGroupedDailyMarketSummaryResponse response
    ) {
        Instant marketTimestamp = extractMarketTimestamp(response);

        if (marketTimestamp == null) {
            log.info("[POLYGON] Grouped daily summary returned no usable timestamp yet.");
            return;
        }

        if (stockMarketDataRepository.existsByMarketTimestamp(marketTimestamp)) {
            log.info("[POLYGON] Daily market summary for {} already imported. Skipping duplicate run.", marketTimestamp);
            return;
        }

        Map<String, Stock> stocksBySymbol = stocks.stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity(), (left, right) -> left));

        Map<Long, StockMarketData> latestByStockId = stockMarketDataRepository.findLatestForAllStocks().stream()
                .collect(Collectors.toMap(data -> data.getStock().getStockId(), Function.identity(), (left, right) -> left));

        int persisted = 0;
        for (PolygonGroupedDailyMarketSummaryResponse.PolygonGroupedDailyAggregate aggregate : response.results()) {
            Stock stock = stocksBySymbol.get(aggregate.ticker());
            if (stock == null) {
                continue;
            }

            persistAggregate(stock, aggregate, latestByStockId.get(stock.getStockId()));
            persisted++;
        }

        log.info("[POLYGON] Imported grouped daily summary for {} stocks at {}", persisted, marketTimestamp);
    }

    private Instant extractMarketTimestamp(PolygonGroupedDailyMarketSummaryResponse response) {
        return response.results().stream()
                .findFirst()
                .map(aggregate -> aggregate.t() > 0 ? Instant.ofEpochMilli(aggregate.t()) : null)
                .orElse(null);
    }

    private void persistAggregate(
            Stock stock,
            PolygonGroupedDailyMarketSummaryResponse.PolygonGroupedDailyAggregate aggregate,
            StockMarketData latestMarketData
    ) {
        BigDecimal openPrice = BigDecimal.valueOf(aggregate.o()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal highPrice = BigDecimal.valueOf(aggregate.h()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal lowPrice = BigDecimal.valueOf(aggregate.l()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal closePrice = BigDecimal.valueOf(aggregate.c()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal previousPrice = latestMarketData == null ? null : latestMarketData.getClosePrice();

        BigDecimal changePercent = BigDecimal.ZERO;
        if (previousPrice != null && previousPrice.compareTo(BigDecimal.ZERO) > 0) {
            changePercent = closePrice.subtract(previousPrice)
                    .divide(previousPrice, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        Instant marketTs = aggregate.t() > 0 ? Instant.ofEpochMilli(aggregate.t()) : Instant.now();

        StockMarketData marketData = new StockMarketData();
        marketData.setStock(stock);
        marketData.setMarketTimestamp(marketTs);
        marketData.setOpenPrice(openPrice);
        marketData.setHighPrice(highPrice);
        marketData.setLowPrice(lowPrice);
        marketData.setClosePrice(closePrice);
        marketData.setVolume(Math.round(aggregate.v()));
        marketData.setChangePercent(changePercent);
        marketData.setSource("massive-grouped-daily");
        stockMarketDataRepository.save(marketData);

        StockResponseDTO stockDTO = StockMapper.toDTO(stock, marketData);
        messagingTemplate.convertAndSend("/topic/stocks", stockDTO);

        log.info("[POLYGON] ✅ Persisted {} directly to DB | OldPrice: ${} → NewPrice: ${} | Change: {}%",
                stock.getSymbol(),
                previousPrice,
                closePrice,
                changePercent);
    }
}
