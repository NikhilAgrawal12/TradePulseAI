package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.market.PolygonTickerReferenceResponse;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.repository.StockRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockUniverseBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(StockUniverseBootstrapService.class);

    private final StockRepository stockRepository;
    private final PolygonMarketDataClient polygonMarketDataClient;
    private final boolean universeSyncEnabled;
    private final int targetSize;
    private final int pageSize;

    public StockUniverseBootstrapService(
            StockRepository stockRepository,
            PolygonMarketDataClient polygonMarketDataClient,
            @Value("${polygon.universe.sync-enabled:true}") boolean universeSyncEnabled,
            @Value("${polygon.universe.target-size:700}") int targetSize,
            @Value("${polygon.universe.page-size:1000}") int pageSize
    ) {
        this.stockRepository = stockRepository;
        this.polygonMarketDataClient = polygonMarketDataClient;
        this.universeSyncEnabled = universeSyncEnabled;
        this.targetSize = targetSize;
        this.pageSize = pageSize;
    }

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapUniverse() {
        if (!universeSyncEnabled) {
            return;
        }

        if (!polygonMarketDataClient.isConfigured()) {
            log.warn("Skipping Polygon universe bootstrap because POLYGON_API_KEY is not configured.");
            return;
        }

        long existingCount = stockRepository.count();
        if (existingCount >= targetSize) {
            log.info("Stock universe already has {} records (target {}).", existingCount, targetSize);
            return;
        }

        List<PolygonTickerReferenceResponse.PolygonTickerReference> tickers =
                polygonMarketDataClient.fetchActiveUsTickers(targetSize, pageSize);

        if (tickers.isEmpty()) {
            log.warn("Polygon universe bootstrap returned no tickers.");
            return;
        }

        int created = 0;
        long currentCount = existingCount;
        for (PolygonTickerReferenceResponse.PolygonTickerReference ticker : tickers) {
            Stock stock = stockRepository.findBySymbol(ticker.ticker()).orElseGet(Stock::new);
            boolean isNew = stock.getStockId() == null;

            stock.setSymbol(ticker.ticker());
            stock.setName(ticker.name());
            stock.setExchange(ticker.primaryExchange());
            stock.setMarket(ticker.market());
            stock.setLocale(ticker.locale());
            stock.setActive(ticker.active());
            stock.setSource("polygon-reference");

            stockRepository.save(stock);
            if (isNew) {
                created++;
                currentCount++;
            }

            if (currentCount >= targetSize) {
                break;
            }
        }

        log.info("Polygon universe bootstrap completed. Created {} records. Current stock count={}",
                created, currentCount);
    }
}
