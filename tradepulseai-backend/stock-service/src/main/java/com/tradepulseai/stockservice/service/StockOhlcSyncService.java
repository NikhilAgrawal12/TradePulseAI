package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
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

@Component
@Order(3)
public class StockOhlcSyncService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockOhlcSyncService.class);

    private final StockRepository stockRepository;
    private final StockMarketDataRepository stockMarketDataRepository;
    private final RestClient restClient;
    private final String apiKey;
    private final boolean syncOnStartup;
    private final boolean dailySyncEnabled;
    private final int yearsBack;
    private final boolean adjusted;
    private final boolean includeOtc;
    private final ZoneId syncZone;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public StockOhlcSyncService(
            StockRepository stockRepository,
            StockMarketDataRepository stockMarketDataRepository,
            @Value("${massive.api.base-url}") String apiBaseUrl,
            @Value("${massive.api.key:}") String apiKey,
            @Value("${massive.ohlc.sync-on-startup:true}") boolean syncOnStartup,
            @Value("${massive.ohlc.daily-sync-enabled:true}") boolean dailySyncEnabled,
            @Value("${massive.ohlc.years-back:3}") int yearsBack,
            @Value("${massive.ohlc.adjusted:true}") boolean adjusted,
            @Value("${massive.ohlc.include-otc:false}") boolean includeOtc,
            @Value("${massive.ohlc.sync-zone:UTC}") String syncZone) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.apiKey = apiKey;
        this.syncOnStartup = syncOnStartup;
        this.dailySyncEnabled = dailySyncEnabled;
        this.yearsBack = yearsBack;
        this.adjusted = adjusted;
        this.includeOtc = includeOtc;
        this.syncZone = ZoneId.of(syncZone);
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .build();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!syncOnStartup) {
            return;
        }
        syncMissingData("startup");
    }

    @Scheduled(cron = "${massive.ohlc.daily-cron:0 30 22 * * *}", zone = "${massive.ohlc.sync-zone:UTC}")
    public void scheduledSync() {
        if (!dailySyncEnabled) {
            return;
        }
        syncMissingData("scheduled");
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
                return;
            }

            int totalInserted = 0;
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                if (isWeekend(date)) {
                    continue;
                }
                totalInserted += syncSingleDate(date, stockByTicker);
            }

            log.info("OHLC sync ({}) completed. Inserted {} rows from {} to {}.", trigger, totalInserted, startDate, endDate);
        } finally {
            running.set(false);
        }
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
            String ticker = normalize(text(node.path("T").asText(null)));
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
        return BigDecimal.valueOf(node.asDouble());
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

