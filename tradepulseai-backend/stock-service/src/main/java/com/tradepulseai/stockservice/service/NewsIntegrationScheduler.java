package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled service to fetch daily news and update sentiment for stocks.
 * Runs daily after market close to aggregate news for that trading day.
 */
@Component
@EnableScheduling
@Order(4)
public class NewsIntegrationScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NewsIntegrationScheduler.class);

    private final NewsService newsService;
    private final StockRepository stockRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NewsIntegrationScheduler(
            NewsService newsService,
            StockRepository stockRepository) {
        this.newsService = newsService;
        this.stockRepository = stockRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("News Integration Scheduler initialized");
    }

    /**
     * Runs daily at 4 PM UTC (after US market close)
     * Fetches news for the trading day and updates sentiment
     */
    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "UTC")
    public void fetchDailyNews() {
        if (running.getAndSet(true)) {
            log.warn("News fetch already in progress, skipping");
            return;
        }

        try {
            LocalDate tradingDate = LocalDate.now(ZoneId.of("UTC"));
            log.info("Starting daily news fetch for {}", tradingDate);

            List<Stock> stocks = stockRepository.findAllByOrderByStockIdAsc();
            int processed = 0;
            int errors = 0;

            for (Stock stock : stocks) {
                try {
                    newsService.fetchAndUpdateNewsSentiment(stock, tradingDate);
                    processed++;

                    // Rate limit to avoid API throttling
                    if (processed % 10 == 0) {
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    log.error("Error fetching news for stock {}", stock.getSymbol(), e);
                    errors++;
                }
            }

            log.info("Daily news fetch completed: {} processed, {} errors", processed, errors);

        } catch (Exception e) {
            log.error("Error in daily news fetch", e);
        } finally {
            running.set(false);
        }
    }

    /**
     * Manual trigger for backfilling historical news for a specific stock
     */
    public void backfillNewsForStock(String ticker, int daysBack) {
        log.info("Starting backfill of news for {} (last {} days)", ticker, daysBack);

        try {
            Stock stock = stockRepository.findBySymbol(ticker)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + ticker));

            LocalDate endDate = LocalDate.now();
            for (int i = 0; i < daysBack; i++) {
                LocalDate date = endDate.minusDays(i);
                // Skip weekends
                if (date.getDayOfWeek().getValue() < 6) {
                    newsService.fetchAndUpdateNewsSentiment(stock, date);
                    Thread.sleep(50); // Rate limit
                }
            }

            log.info("Backfill completed for {}", ticker);
        } catch (Exception e) {
            log.error("Error backfilling news for {}", ticker, e);
        }
    }
}

