package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.market.PolygonPreviousCloseResponse;
import com.tradepulseai.stockservice.dto.market.StockMarketEvent;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.repository.StockRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PolygonStockIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolygonStockIngestionScheduler.class);

    private final StockRepository stockRepository;
    private final PolygonMarketDataClient polygonMarketDataClient;
    private final StockMarketKafkaPublisher stockMarketKafkaPublisher;
    private final boolean fetchEnabled;

    private final AtomicInteger nextIndex = new AtomicInteger(0);
    private final AtomicBoolean missingKeyLogged = new AtomicBoolean(false);

    public PolygonStockIngestionScheduler(
            StockRepository stockRepository,
            PolygonMarketDataClient polygonMarketDataClient,
            StockMarketKafkaPublisher stockMarketKafkaPublisher,
            @Value("${polygon.fetch.enabled:false}") boolean fetchEnabled
    ) {
        this.stockRepository = stockRepository;
        this.polygonMarketDataClient = polygonMarketDataClient;
        this.stockMarketKafkaPublisher = stockMarketKafkaPublisher;
        this.fetchEnabled = fetchEnabled;
    }

    @Transactional
    @Scheduled(fixedDelayString = "#{T(java.lang.Math).max(${polygon.fetch.fixed-delay-ms:12000}, 12000)}", initialDelay = 10000)
    public void fetchAndPublish() {
        if (!fetchEnabled) {
            return;
        }

        if (!polygonMarketDataClient.isConfigured()) {
            if (missingKeyLogged.compareAndSet(false, true)) {
                log.warn("Polygon ingestion enabled but POLYGON_API_KEY is missing.");
            }
            return;
        }

        List<Stock> stocks = stockRepository.findAll(Sort.by(Sort.Direction.ASC, "stockId"));
        if (stocks.isEmpty()) {
            log.debug("No stocks available for Polygon ingestion.");
            return;
        }

        int index = Math.floorMod(nextIndex.getAndIncrement(), stocks.size());
        Stock stock = stocks.get(index);

        polygonMarketDataClient.fetchPreviousClose(stock.getSymbol())
                .ifPresent(aggregate -> processAggregate(stock, aggregate));
    }

    private void processAggregate(Stock stock, PolygonPreviousCloseResponse.PolygonAggregate aggregate) {
        BigDecimal newPrice = BigDecimal.valueOf(aggregate.c()).setScale(4, RoundingMode.HALF_UP);

        BigDecimal changePercent = BigDecimal.ZERO;
        if (stock.getPrice() != null && stock.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            changePercent = newPrice.subtract(stock.getPrice())
                    .divide(stock.getPrice(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        Instant marketTs = aggregate.t() > 0 ? Instant.ofEpochMilli(aggregate.t()) : Instant.now();

        StockMarketEvent event = new StockMarketEvent(
                stock.getSymbol(),
                newPrice,
                changePercent,
                aggregate.v(),
                marketTs,
                "polygon"
        );

        stockMarketKafkaPublisher.publish(event);
    }
}
