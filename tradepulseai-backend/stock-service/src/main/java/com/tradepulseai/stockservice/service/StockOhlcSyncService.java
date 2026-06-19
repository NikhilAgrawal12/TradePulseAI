package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Order(3)
public class StockOhlcSyncService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockOhlcSyncService.class);

    private final StockRepository stockRepository;
    private final StockMarketDataRepository stockMarketDataRepository;
    private final StockMetricsRefreshService stockMetricsRefreshService;
    private final RestClient restClient;
    private final String apiKey;
    private final boolean syncOnStartup;
    private final boolean dailySyncEnabled;
    private final int yearsBack;
    private final long refreshInitialDelayMinutes;
    private final long refreshIntervalMinutes;
    private final boolean adjusted;
    private final boolean includeOtc;
    private final ZoneId syncZone;
    private final ScheduledExecutorService refreshExecutor;
    private final AtomicBoolean refreshLoopStarted = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public StockOhlcSyncService(
            StockRepository stockRepository,
            StockMarketDataRepository stockMarketDataRepository,
            StockMetricsRefreshService stockMetricsRefreshService,
            @Value("${massive.api.base-url}") String apiBaseUrl,
            @Value("${massive.api.key:}") String apiKey,
            @Value("${massive.ohlc.sync-on-startup:true}") boolean syncOnStartup,
            @Value("${massive.ohlc.daily-sync-enabled:true}") boolean dailySyncEnabled,
            @Value("${massive.ohlc.years-back:3}") int yearsBack,
            @Value("${massive.ohlc.refresh-initial-delay-minutes:1}") long refreshInitialDelayMinutes,
            @Value("${massive.ohlc.refresh-interval-minutes:30}") long refreshIntervalMinutes,
            @Value("${massive.ohlc.adjusted:true}") boolean adjusted,
            @Value("${massive.ohlc.include-otc:false}") boolean includeOtc,
            @Value("${massive.ohlc.sync-zone:UTC}") String syncZone) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.stockMetricsRefreshService = stockMetricsRefreshService;
        this.apiKey = apiKey;
        this.syncOnStartup = syncOnStartup;
        this.dailySyncEnabled = dailySyncEnabled;
        this.yearsBack = yearsBack;
        this.refreshInitialDelayMinutes = Math.max(1L, refreshInitialDelayMinutes);
        this.refreshIntervalMinutes = Math.max(1L, refreshIntervalMinutes);
        this.adjusted = adjusted;
        this.includeOtc = includeOtc;
        this.syncZone = ZoneId.of(syncZone);
        this.refreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "stock-ohlc-refresh");
            thread.setDaemon(true);
            return thread;
        });
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .build();
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (syncOnStartup || dailySyncEnabled) {
            ensureApiKeyConfigured();
        }

        if (dailySyncEnabled) {
            startBackgroundRefreshLoop();
        }

        if (syncOnStartup) {
            syncMissingData("startup");
        }
    }

    private void syncMissingData(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.info("Skipping {} OHLC sync because another sync is already running.", trigger);
            return;
        }

        try {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("massive.api.key is required for OHLC sync");
            }

            List<Stock> stocks = stockRepository.findAllByOrderByStockIdAsc();
            if (stocks.isEmpty()) {
                log.info("Skipping {} OHLC sync because stocks table is empty.", trigger);
                return;
            }

            Map<String, Stock> stockByTicker = new HashMap<>();
            for (Stock stock : stocks) {
                String symbol = normalize(stock.getSymbol());
                if (symbol != null) {
                    stockByTicker.put(symbol, stock);
                }
            }

            LocalDate endDate = LocalDate.now(syncZone);
            LocalDate configuredStart = endDate.minusYears(yearsBack);
            LocalDate startDate = stockMarketDataRepository.findMaxTradingDate()
                    .map(date -> date.plusDays(1))
                    .orElse(configuredStart);

            if (startDate.isBefore(configuredStart)) {
                startDate = configuredStart;
            }

            if (startDate.isAfter(endDate)) {
                log.info("No missing OHLC dates to sync for trigger {}.", trigger);
                if ("startup".equalsIgnoreCase(trigger)) {
                    stockMetricsRefreshService.refreshAllForLatestOhlc(trigger);
                }
                return;
            }

            int totalInserted = 0;
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                if (isWeekend(date)) {
                    continue;
                }
                totalInserted += syncSingleDate(date, stockByTicker);
            }

            if (totalInserted > 0 || "startup".equalsIgnoreCase(trigger)) {
                stockMetricsRefreshService.refreshAllForLatestOhlc(trigger);
            }

            log.info("OHLC sync ({}) completed. Inserted {} rows from {} to {}.", trigger, totalInserted, startDate, endDate);
        } finally {
            running.set(false);
        }
    }

    private void startBackgroundRefreshLoop() {
        if (!refreshLoopStarted.compareAndSet(false, true)) {
            return;
        }

        refreshExecutor.scheduleWithFixedDelay(() -> {
            try {
                syncMissingData("background");
            } catch (Exception ex) {
                log.error("Unexpected failure during background OHLC sync.", ex);
            }
        }, refreshInitialDelayMinutes, refreshIntervalMinutes, TimeUnit.MINUTES);

        log.info("Started background OHLC refresh loop with initial delay {} minute(s) and interval {} minute(s).",
                refreshInitialDelayMinutes, refreshIntervalMinutes);
    }

    private void ensureApiKeyConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("massive.api.key is required for OHLC sync");
        }
    }

    @PreDestroy
    public void shutdown() {
        refreshExecutor.shutdownNow();
    }

    private int syncSingleDate(LocalDate date, Map<String, Stock> stockByTicker) {
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(buildGroupedDailyPath(date))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("No grouped OHLC data for {}.", date);
            return 0;
        }

        if (response == null) {
            throw new IllegalStateException("Massive API returned empty response for grouped OHLC endpoint on " + date);
        }

        JsonNode resultsNode = response.path("results");
        if (!resultsNode.isArray()) {
            return 0;
        }

        boolean adjustedResponse = response.path("adjusted").asBoolean(adjusted);
        Set<Long> existingStockIds = new HashSet<>(stockMarketDataRepository.findStockIdsByTradingDate(date));
        List<StockMarketData> rows = new java.util.ArrayList<>();

        for (JsonNode node : resultsNode) {
            JsonNode tickerNode = node.get("T");
            String ticker = normalize(text(tickerNode == null || tickerNode.isNull() ? null : tickerNode.asText()));
            if (ticker == null) {
                continue;
            }

            Stock stock = stockByTicker.get(ticker);
            if (stock == null || stock.getStockId() == null || existingStockIds.contains(stock.getStockId())) {
                continue;
            }

            BigDecimal open = decimalOrNull(node.get("o"));
            BigDecimal high = decimalOrNull(node.get("h"));
            BigDecimal low = decimalOrNull(node.get("l"));
            BigDecimal close = decimalOrNull(node.get("c"));
            if (open == null || high == null || low == null || close == null) {
                continue;
            }

            StockMarketData row = new StockMarketData();
            row.setStock(stock);
            row.setTradingDate(date);
            row.setOpenPrice(open);
            row.setHighPrice(high);
            row.setLowPrice(low);
            row.setClosePrice(close);
            row.setVolume(longOrZero(node.get("v")));
            row.setVwap(decimalOrNull(node.get("vw")));
            row.setOtc(node.path("otc").asBoolean(false));
            row.setAdjusted(adjustedResponse);
            rows.add(row);
            existingStockIds.add(stock.getStockId());
        }

        if (rows.isEmpty()) {
            return 0;
        }

        stockMarketDataRepository.saveAll(rows);
        log.info("Inserted {} OHLC rows for {}.", rows.size(), date);
        return rows.size();
    }

    private String buildGroupedDailyPath(LocalDate date) {
        return UriComponentsBuilder.fromPath("/v2/aggs/grouped/locale/us/market/stocks/{date}")
                .queryParam("adjusted", adjusted)
                .queryParam("include_otc", includeOtc)
                .queryParam("apiKey", apiKey)
                .buildAndExpand(date)
                .toUriString();
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private BigDecimal decimalOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return BigDecimal.valueOf(node.asDouble()).setScale(2, RoundingMode.HALF_UP);
    }

    private long longOrZero(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0L;
        }
        return Math.round(node.asDouble());
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalize(String value) {
        String text = text(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }
}

