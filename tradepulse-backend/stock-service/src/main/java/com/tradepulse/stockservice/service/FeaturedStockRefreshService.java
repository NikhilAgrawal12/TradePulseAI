package com.tradepulse.stockservice.service;

import com.tradepulse.stockservice.model.Stock;
import com.tradepulse.stockservice.model.StockMarketData;
import com.tradepulse.stockservice.model.FeaturedStockCache;
import com.tradepulse.stockservice.repository.StockMarketDataRepository;
import com.tradepulse.stockservice.repository.StockRepository;
import com.tradepulse.stockservice.repository.FeaturedStockCacheRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Order(4)
public class FeaturedStockRefreshService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FeaturedStockRefreshService.class);

    private final StockRepository stockRepository;
    private final StockMarketDataRepository stockMarketDataRepository;
    private final FeaturedStockCacheRepository featuredStockCacheRepository;
    private final RestClient restClient;
    private final String apiKey;
    private final boolean dailyRefreshEnabled;
    private final ScheduledExecutorService refreshExecutor;
    private final AtomicBoolean refreshLoopStarted = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FeaturedStockRefreshService(
            StockRepository stockRepository,
            StockMarketDataRepository stockMarketDataRepository,
            FeaturedStockCacheRepository featuredStockCacheRepository,
            @Value("${massive.api.base-url}") String apiBaseUrl,
            @Value("${massive.api.key:}") String apiKey,
            @Value("${massive.featured.daily-refresh-enabled:true}") boolean dailyRefreshEnabled) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.featuredStockCacheRepository = featuredStockCacheRepository;
        this.apiKey = apiKey;
        this.dailyRefreshEnabled = dailyRefreshEnabled;
        this.refreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "featured-stock-refresh");
            thread.setDaemon(true);
            return thread;
        });
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .build();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (dailyRefreshEnabled) {
            ensureApiKeyConfigured();
            startBackgroundRefreshLoop();
        }

        // Service startup is INSTANT - no calculations
        // Ranking happens at scheduled time every day (e.g., 9:00 AM)
        // Previous day's cache is still available even if service was down
        log.info("Featured stock refresh service initialized. Daily refresh enabled: {}", dailyRefreshEnabled);
    }

    public void triggerManualRefresh() {
        ensureApiKeyConfigured();

        refreshExecutor.execute(() -> {
            try {
                refreshFeaturedStocks("manual");
            } catch (Exception ex) {
                log.error("Unexpected failure during manual featured-stock refresh.", ex);
            }
        });
    }

    private void refreshFeaturedStocks(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.info("Skipping {} featured-stock refresh because another refresh is already running.", trigger);
            return;
        }

        try {
            List<Stock> stocks = stockRepository.findAllByOrderByStockIdAsc();
            if (stocks.isEmpty()) {
                log.info("Skipping {} featured-stock refresh because stocks table is empty.", trigger);
                return;
            }

            // Step 1: Update market caps (only updating the market_cap column, not adding/removing stocks)
            int refreshedMarketCaps = 0;
            int processed = 0;
            for (Stock stock : stocks) {
                String symbol = normalize(stock.getSymbol());
                if (symbol == null) {
                    continue;
                }

                BigDecimal marketCap = fetchMarketCap(symbol);
                if (marketCap != null && marketCap.signum() > 0) {
                    stock.setMarketCap(marketCap);
                    refreshedMarketCaps++;
                }

                processed++;
                if (processed % 250 == 0) {
                    log.info("Processed {} market-cap refreshes so far.", processed);
                }
            }

            // Save market cap updates
            stockRepository.saveAll(stocks);

            // Step 2: Compute ranking for all stocks with latest quote.
            // We still sort by market cap first so /stocks/featured can return top-ranked items.
            Map<Long, StockMarketData> latestByStockId = new HashMap<>();
            stockMarketDataRepository.findLatestForAllStocks()
                    .forEach(data -> latestByStockId.put(data.getStock().getStockId(), data));

            Set<Long> stockIdsWithLatestQuote = latestByStockId.keySet();

            List<Stock> rankedStocks = stocks.stream()
                    .filter(stock -> stock.getStockId() != null && stockIdsWithLatestQuote.contains(stock.getStockId()))
                    .sorted(Comparator
                            .comparing(Stock::getMarketCap, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(stock -> normalize(stock.getSymbol()), Comparator.nullsLast(String::compareTo)))
                    .toList();

            if (rankedStocks.isEmpty()) {
                log.warn("Skipping featured-stock cache update for trigger {} because no stocks with latest quote were available.", trigger);
                return;
            }

            // Step 3: Limit to top 50 and update cache with ranking only (no quote data)
             int targetCount = 50;
             List<Stock> topFeatured = rankedStocks.stream().limit(targetCount).toList();

             featuredStockCacheRepository.deleteAll();
             List<FeaturedStockCache> cacheEntries = new java.util.ArrayList<>();
             for (int index = 0; index < topFeatured.size(); index++) {
                 Stock stock = topFeatured.get(index);

                 FeaturedStockCache entry = new FeaturedStockCache();
                 entry.setStock(stock);
                 entry.setSortOrder(index + 1);
                 cacheEntries.add(entry);
             }
             featuredStockCacheRepository.saveAll(cacheEntries);

             log.info("Featured-stock refresh ({}) completed. Cached top {} featured stocks, refreshed {} market caps. Stocks table remains unchanged with {} stocks.",
                     trigger, cacheEntries.size(), refreshedMarketCaps, stocks.size());
        } finally {
            running.set(false);
        }
    }

    private void startBackgroundRefreshLoop() {
        if (!refreshLoopStarted.compareAndSet(false, true)) {
            return;
        }

        // Calculate initial delay until 9:00 AM (configurable via property)
        Calendar now = Calendar.getInstance();
        Calendar next9Am = Calendar.getInstance();
        next9Am.set(Calendar.HOUR_OF_DAY, 9);
        next9Am.set(Calendar.MINUTE, 0);
        next9Am.set(Calendar.SECOND, 0);

        // If 9 AM has already passed today, schedule for tomorrow
        if (now.after(next9Am)) {
            next9Am.add(Calendar.DAY_OF_MONTH, 1);
        }

        long initialDelayMillis = next9Am.getTimeInMillis() - System.currentTimeMillis();
        long dailyIntervalMillis = 24 * 60 * 60 * 1000; // 24 hours

        refreshExecutor.scheduleAtFixedRate(() -> {
            try {
                refreshFeaturedStocks("scheduled");
            } catch (Exception ex) {
                log.error("Unexpected failure during scheduled featured-stock refresh.", ex);
            }
        }, initialDelayMillis, dailyIntervalMillis, java.util.concurrent.TimeUnit.MILLISECONDS);

        log.info("Featured stock refresh loop started. First refresh at 9:00 AM, then daily at 9:00 AM.");
    }

    private BigDecimal fetchMarketCap(String ticker) {
        try {
            JsonNode response = restClient.get()
                    .uri(buildTickerOverviewPath(ticker))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return null;
            }

            JsonNode result = response.path("results");
            if (result.isMissingNode() || result.isNull() || !result.isObject()) {
                return null;
            }

            double marketCap = numberOrZero(result.get("market_cap"));
            return marketCap > 0 ? BigDecimal.valueOf(marketCap) : null;
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("Skipping ticker {} because Massive returned 404 from ticker overview endpoint.", ticker);
            return null;
        } catch (Exception ex) {
            log.warn("Unable to refresh market cap for ticker {}: {}", ticker, ex.getMessage());
            return null;
        }
    }

    private String buildTickerOverviewPath(String ticker) {
        return UriComponentsBuilder.fromPath("/v3/reference/tickers/{ticker}")
                .queryParam("apiKey", apiKey)
                .buildAndExpand(ticker)
                .toUriString();
    }


    private void ensureApiKeyConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("massive.api.key is required for featured-stock refresh");
        }
    }

    private double numberOrZero(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0d;
        }
        return node.asDouble(0d);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }


    @PreDestroy
    public void shutdown() {
        refreshExecutor.shutdownNow();
    }
}


