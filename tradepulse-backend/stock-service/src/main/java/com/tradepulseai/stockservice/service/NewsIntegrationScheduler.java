package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.repository.StockRepository;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
    private final StockMarketDataRepository stockMarketDataRepository;
    private final boolean dailySchedulerEnabled;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean backfillRunning = new AtomicBoolean(false);
    private volatile BackfillStatusSnapshot lastBackfillStatus = BackfillStatusSnapshot.idle();
    private final ScheduledExecutorService backfillExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "news-backfill-worker");
        thread.setDaemon(true);
        return thread;
    });

    public NewsIntegrationScheduler(
            NewsService newsService,
            StockRepository stockRepository,
            StockMarketDataRepository stockMarketDataRepository,
            @Value("${massive.news.daily-scheduler-enabled:false}") boolean dailySchedulerEnabled) {
        this.newsService = newsService;
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.dailySchedulerEnabled = dailySchedulerEnabled;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("News Integration Scheduler initialized");
    }

    @PreDestroy
    public void shutdown() {
        backfillExecutor.shutdownNow();
    }

    /**
     * Runs daily at 5:30 PM America/New_York (after US market close)
     * Fetches news for the trading day and updates sentiment.
     */
    @Scheduled(cron = "0 30 17 * * MON-FRI", zone = "America/New_York")
    public void fetchDailyNews() {
        if (!dailySchedulerEnabled) {
            log.debug("Daily scheduled news fetch is disabled");
            return;
        }

        if (running.getAndSet(true)) {
            log.warn("News fetch already in progress, skipping");
            return;
        }

        try {
            LocalDate tradingDate = LocalDate.now(ZoneId.of("UTC"));
            log.info("Starting daily news fetch for {}", tradingDate);

            List<StockMarketData> marketRows = stockMarketDataRepository.findAllByTradingDate(tradingDate);
            int processed = 0;
            int errors = 0;

            for (StockMarketData marketRow : marketRows) {
                try {
                    newsService.fetchAndUpdateNewsSentiment(marketRow);
                    processed++;

                    // Rate limit to avoid API throttling
                    if (processed % 10 == 0) {
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    log.error("Error fetching news for stock {}", marketRow.getStock().getSymbol(), e);
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
            String normalizedTicker = ticker == null ? null : ticker.trim().toUpperCase(Locale.ROOT);
            Stock stock = stockRepository.findBySymbol(normalizedTicker)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + ticker));

            LocalDate endDate = LocalDate.now();
            for (int i = 0; i < daysBack; i++) {
                LocalDate date = endDate.minusDays(i);
                // Skip weekends
                if (date.getDayOfWeek().getValue() < 6) {
                    stockMarketDataRepository.findByStockAndTradingDate(stock, date)
                            .ifPresent(newsService::fetchAndUpdateNewsSentiment);
                    Thread.sleep(50); // Rate limit
                }
            }

            log.info("Backfill completed for {}", ticker);
        } catch (Exception e) {
            log.error("Error backfilling news for {}", ticker, e);
        }
    }

    public boolean triggerBackfillNewsForStock(String ticker, int daysBack) {
        int safeDays = Math.max(1, daysBack);
        if (!backfillRunning.compareAndSet(false, true)) {
            return false;
        }

        backfillExecutor.submit(() -> {
            try {
                backfillNewsForStock(ticker, safeDays);
            } finally {
                backfillRunning.set(false);
            }
        });
        return true;
    }

    /**
     * Backfill news/sentiment for all stocks over a trailing date window.
     */
    public void backfillNewsForAllStocks(int daysBack) {
        log.info("Starting backfill of news for all stocks (last {} days)", daysBack);

        try {
            List<Stock> stocks = stockRepository.findAllByOrderByStockIdAsc();
            LocalDate endDate = LocalDate.now();

            for (int i = 0; i < daysBack; i++) {
                LocalDate date = endDate.minusDays(i);
                if (date.getDayOfWeek().getValue() >= 6) {
                    continue;
                }

                for (Stock stock : stocks) {
                    stockMarketDataRepository.findByStockAndTradingDate(stock, date)
                            .ifPresent(newsService::fetchAndUpdateNewsSentiment);

                    Thread.sleep(25);
                }
            }

            log.info("Backfill completed for all stocks");
        } catch (Exception e) {
            log.error("Error backfilling news for all stocks", e);
        }
    }

    public boolean triggerBackfillNewsForAllStocks(int daysBack) {
        int safeDays = Math.max(1, daysBack);
        if (!backfillRunning.compareAndSet(false, true)) {
            return false;
        }

        lastBackfillStatus = BackfillStatusSnapshot.queued("all", safeDays, 0, LocalDate.now(), null);

        backfillExecutor.submit(() -> {
            try {
                backfillNewsForAllStocks(safeDays);
            } finally {
                backfillRunning.set(false);
            }
        });
        return true;
    }

    /**
     * Backfill news/sentiment for top-N stocks by market cap over a trailing date window.
     * If resumeFromDate is provided, backfill starts from that date instead of current date.
     */
    public void backfillNewsForTopStocks(int daysBack, int stockLimit, LocalDate resumeFromDate) {
        int safeDays = Math.max(1, daysBack);
        int safeLimit = Math.max(1, stockLimit);
        LocalDate startDate = resumeFromDate != null ? resumeFromDate : LocalDate.now(ZoneId.of("UTC"));
        log.info("Starting backfill of news for top {} stocks by market cap (last {} days, startDate={})",
                safeLimit, safeDays, startDate);

        BackfillStatusSnapshot status = BackfillStatusSnapshot.running("top", safeDays, safeLimit, startDate, resumeFromDate);
        lastBackfillStatus = status;

        try {
            List<Stock> stocks = stockRepository.findTopByMarketCap(PageRequest.of(0, safeLimit));
            int processedDays = 0;
            int updatedRows = 0;
            int attemptedRows = 0;
            int errors = 0;

            for (int i = 0; i < safeDays; i++) {
                LocalDate date = startDate.minusDays(i);
                if (date.getDayOfWeek().getValue() >= 6) {
                    continue;
                }

                status.currentDate = date;
                processedDays++;

                for (Stock stock : stocks) {
                    try {
                        attemptedRows++;
                        if (stockMarketDataRepository.findByStockAndTradingDate(stock, date)
                                .map(row -> {
                                    newsService.fetchAndUpdateNewsSentiment(row);
                                    return true;
                                })
                                .orElse(false)) {
                            updatedRows++;
                        }
                        Thread.sleep(25);
                    } catch (Exception inner) {
                        errors++;
                        log.error("Error backfilling news for {} on {}", stock.getSymbol(), date, inner);
                    }
                }

                status.processedDays = processedDays;
                status.updatedRows = updatedRows;
                status.attemptedRows = attemptedRows;
                status.errors = errors;
            }

            status.running = false;
            status.finishedAt = Instant.now();
            status.message = "Backfill completed for top stocks by market cap";
            log.info("Backfill completed for top {} stocks by market cap: {} updated rows across {} processed days",
                    safeLimit, updatedRows, processedDays);
        } catch (Exception e) {
            status.running = false;
            status.finishedAt = Instant.now();
            status.message = "Backfill failed";
            log.error("Error backfilling news for top stocks by market cap", e);
        }
    }

    public boolean triggerBackfillNewsForTopStocks(int daysBack, int stockLimit, LocalDate resumeFromDate) {
        int safeDays = Math.max(1, daysBack);
        int safeLimit = Math.max(1, stockLimit);
        if (!backfillRunning.compareAndSet(false, true)) {
            return false;
        }

        LocalDate startDate = resumeFromDate != null ? resumeFromDate : LocalDate.now(ZoneId.of("UTC"));
        lastBackfillStatus = BackfillStatusSnapshot.queued("top", safeDays, safeLimit, startDate, resumeFromDate);

        backfillExecutor.submit(() -> {
            try {
                backfillNewsForTopStocks(safeDays, safeLimit, resumeFromDate);
            } finally {
                backfillRunning.set(false);
            }
        });
        return true;
    }

    public Map<String, Object> getBackfillStatus() {
        BackfillStatusSnapshot status = lastBackfillStatus;
        Map<String, Object> response = new HashMap<>();
        response.put("running", backfillRunning.get());
        response.put("mode", status.mode);
        response.put("daysBack", status.daysBack);
        response.put("stockLimit", status.stockLimit);
        response.put("startDate", status.startDate == null ? null : status.startDate.toString());
        response.put("resumeFromDate", status.resumeFromDate == null ? null : status.resumeFromDate.toString());
        response.put("currentDate", status.currentDate == null ? null : status.currentDate.toString());
        response.put("processedDays", status.processedDays);
        response.put("updatedRows", status.updatedRows);
        response.put("attemptedRows", status.attemptedRows);
        response.put("errors", status.errors);
        response.put("startedAt", status.startedAt == null ? null : status.startedAt.toString());
        response.put("finishedAt", status.finishedAt == null ? null : status.finishedAt.toString());
        response.put("message", status.message);
        return response;
    }

    private static final class BackfillStatusSnapshot {
        private String mode;
        private boolean running;
        private int daysBack;
        private int stockLimit;
        private LocalDate startDate;
        private LocalDate resumeFromDate;
        private LocalDate currentDate;
        private int processedDays;
        private int updatedRows;
        private int attemptedRows;
        private int errors;
        private Instant startedAt;
        private Instant finishedAt;
        private String message;

        private static BackfillStatusSnapshot idle() {
            BackfillStatusSnapshot status = new BackfillStatusSnapshot();
            status.mode = "idle";
            status.running = false;
            status.message = "No backfill has run yet.";
            return status;
        }

        private static BackfillStatusSnapshot queued(String mode, int daysBack, int stockLimit,
                                                     LocalDate startDate, LocalDate resumeFromDate) {
            BackfillStatusSnapshot status = new BackfillStatusSnapshot();
            status.mode = mode;
            status.running = true;
            status.daysBack = daysBack;
            status.stockLimit = stockLimit;
            status.startDate = startDate;
            status.resumeFromDate = resumeFromDate;
            status.message = "Backfill job queued";
            status.startedAt = Instant.now();
            return status;
        }

        private static BackfillStatusSnapshot running(String mode, int daysBack, int stockLimit,
                                                      LocalDate startDate, LocalDate resumeFromDate) {
            BackfillStatusSnapshot status = queued(mode, daysBack, stockLimit, startDate, resumeFromDate);
            status.message = "Backfill job running";
            return status;
        }
    }
}

