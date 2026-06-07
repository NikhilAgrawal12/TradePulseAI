package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.model.FeaturedStockCache;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import com.tradepulseai.stockservice.repository.FeaturedStockCacheRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
    private final int targetCount;
    private final ScheduledExecutorService refreshExecutor;
    private final AtomicBoolean refreshLoopStarted = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FeaturedStockRefreshService(
            StockRepository stockRepository,
            StockMarketDataRepository stockMarketDataRepository,
            FeaturedStockCacheRepository featuredStockCacheRepository,
            @Value("${massive.api.base-url}") String apiBaseUrl,
            @Value("${massive.api.key:}") String apiKey,
            @Value("${massive.featured.daily-refresh-enabled:true}") boolean dailyRefreshEnabled,
            @Value("${massive.featured.target-count:50}") int targetCount) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.featuredStockCacheRepository = featuredStockCacheRepository;
        this.apiKey = apiKey;
        this.dailyRefreshEnabled = dailyRefreshEnabled;
        this.targetCount = Math.max(1, targetCount);
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

            // Step 2: Compute top 50 ranking based on market cap
            Set<Long> stockIdsWithLatestQuote = stockMarketDataRepository.findLatestForAllStocks()
                    .stream()
                    .map(StockMarketData::getStock)
                    .map(Stock::getStockId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());

            List<Stock> rankedStocks = stocks.stream()
                    .filter(stock -> Boolean.TRUE.equals(stock.getActive()))
                    .filter(stock -> stock.getStockId() != null && stockIdsWithLatestQuote.contains(stock.getStockId()))
                    .filter(stock -> stock.getMarketCap() != null && stock.getMarketCap().signum() > 0)
                    .sorted(Comparator.comparing(Stock::getMarketCap).reversed()
                            .thenComparing(stock -> normalize(stock.getSymbol()), Comparator.nullsLast(String::compareTo)))
                    .limit(targetCount)
                    .toList();

            if (rankedStocks.isEmpty()) {
                log.warn("Skipping featured-stock cache update for trigger {} because no ranked stocks with market cap and latest quote were available.", trigger);
                return;
            }

            // Step 3: Update cache (replace entire cache with new top 50)
            featuredStockCacheRepository.deleteAll();
            List<FeaturedStockCache> cacheEntries = new java.util.ArrayList<>();
            for (int index = 0; index < rankedStocks.size(); index++) {
                FeaturedStockCache entry = new FeaturedStockCache();
                entry.setStock(rankedStocks.get(index));
                entry.setSortOrder(index + 1);
                cacheEntries.add(entry);
            }
            featuredStockCacheRepository.saveAll(cacheEntries);

            log.info("Featured-stock refresh ({}) completed. Ranked {} featured stocks (cached), refreshed {} market caps. Stocks table remains unchanged with {} stocks.",
                    trigger, rankedStocks.size(), refreshedMarketCaps, stocks.size());
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


