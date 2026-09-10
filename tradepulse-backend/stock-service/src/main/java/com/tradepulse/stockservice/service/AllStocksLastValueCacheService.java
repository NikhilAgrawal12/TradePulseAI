package com.tradepulse.stockservice.service;

import com.tradepulse.stockservice.model.AllStocksLastValueCache;
import com.tradepulse.stockservice.model.FeaturedStockCache;
import com.tradepulse.stockservice.model.Stock;
import com.tradepulse.stockservice.repository.AllStocksLastValueCacheRepository;
import com.tradepulse.stockservice.repository.FeaturedStockCacheRepository;
import com.tradepulse.stockservice.repository.StockRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@Order(5)
public class AllStocksLastValueCacheService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AllStocksLastValueCacheService.class);
    private static final String MASSIVE_DELAYED_WS_URL = "wss://delayed.massive.com/stocks";
    private static final String MASSIVE_API_BASE_URL = "https://api.massive.com";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int SUBSCRIPTION_CHUNK_SIZE = 200;
    private static final int FEATURED_FALLBACK_LIMIT = 50;
    private static final long FEATURED_SNAPSHOT_REFRESH_SECONDS = 15;
    private static final long WEBSOCKET_STALE_SECONDS = 45;

    private final StockRepository stockRepository;
    private final AllStocksLastValueCacheRepository allStocksLastValueCacheRepository;
    private final FeaturedStockCacheRepository featuredStockCacheRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;
    private final String massiveApiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "all-stocks-cache-websocket");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocket webSocket;
    private volatile Map<String, Stock> stockBySymbol = Map.of();
    private final Map<Long, AllStocksLastValueCache> cacheByStockId = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastRealtimeAggregateAt = new AtomicReference<>();

    public AllStocksLastValueCacheService(
            StockRepository stockRepository,
            AllStocksLastValueCacheRepository allStocksLastValueCacheRepository,
            FeaturedStockCacheRepository featuredStockCacheRepository,
            ApplicationEventPublisher eventPublisher,
            JdbcTemplate jdbcTemplate,
            @Value("${massive.api.key:}") String massiveApiKey) {
        this.stockRepository = stockRepository;
        this.allStocksLastValueCacheRepository = allStocksLastValueCacheRepository;
        this.featuredStockCacheRepository = featuredStockCacheRepository;
        this.eventPublisher = eventPublisher;
        this.jdbcTemplate = jdbcTemplate;
        this.massiveApiKey = massiveApiKey;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (massiveApiKey == null || massiveApiKey.isBlank()) {
            log.warn("All-stocks websocket cache disabled because massive.api.key is missing.");
            return;
        }

        loadStocks();
        ensureCacheTableExists();
        warmInMemoryCache();
        connect();
        scheduler.scheduleAtFixedRate(
                this::refreshFeaturedSnapshotFallback,
                FEATURED_SNAPSHOT_REFRESH_SECONDS,
                FEATURED_SNAPSHOT_REFRESH_SECONDS,
                TimeUnit.SECONDS
        );
        log.info("All-stocks websocket cache started for {} symbols.", stockBySymbol.size());
    }

    private void loadStocks() {
        Map<String, Stock> bySymbol = new HashMap<>();
        for (Stock stock : stockRepository.findAllByOrderByStockIdAsc()) {
            String symbol = normalizeSymbol(stock.getSymbol());
            if (symbol != null) {
                bySymbol.put(symbol, stock);
            }
        }
        stockBySymbol = Map.copyOf(bySymbol);
    }

    private void warmInMemoryCache() {
        cacheByStockId.clear();
        try {
            for (AllStocksLastValueCache entry : allStocksLastValueCacheRepository.findAll()) {
                if (entry.getStock() != null && entry.getStock().getStockId() != null) {
                    cacheByStockId.put(entry.getStock().getStockId(), entry);
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to warm all-stocks cache from database. Continuing with live websocket updates only: {}", ex.getMessage());
        }
    }

    private void ensureCacheTableExists() {
        try {
            String tableName = jdbcTemplate.queryForObject(
                    "SELECT to_regclass('public.all_stocks_last_value_cache')",
                    String.class
            );
            if (tableName != null) {
                return;
            }

            log.warn("Table public.all_stocks_last_value_cache is missing. Creating it now to prevent startup errors.");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS all_stocks_last_value_cache (
                        all_stocks_cache_id BIGSERIAL PRIMARY KEY,
                        stock_id BIGINT NOT NULL UNIQUE,
                        cached_open NUMERIC(18, 6) NOT NULL,
                        cached_close NUMERIC(18, 6) NOT NULL,
                        cached_high NUMERIC(18, 6) NOT NULL,
                        cached_low NUMERIC(18, 6) NOT NULL,
                        cached_volume BIGINT NOT NULL,
                        cached_vwap NUMERIC(18, 6) NOT NULL,
                        cached_change_percent NUMERIC(12, 6),
                        aggregate_updated_at TIMESTAMP,
                        cached_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_all_stocks_cache_stock_id FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE
                    )
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_all_stocks_cache_cached_at ON all_stocks_last_value_cache(cached_at DESC)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_all_stocks_cache_aggregate_ts ON all_stocks_last_value_cache(aggregate_updated_at DESC)");
        } catch (Exception ex) {
            log.error("Failed to ensure all_stocks_last_value_cache table exists.", ex);
        }
    }

    private void connect() {
        try {
            httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(MASSIVE_DELAYED_WS_URL), new MassiveWebSocketListener())
                    .whenComplete((socket, error) -> {
                        if (error != null) {
                            log.error("Unable to connect websocket for all-stocks cache.", error);
                            queueReconnect();
                            return;
                        }
                        webSocket = socket;
                    });
        } catch (Exception ex) {
            log.error("Unable to connect websocket for all-stocks cache.", ex);
            queueReconnect();
        }
    }

    private void queueReconnect() {
        scheduler.schedule(this::connect, 2, TimeUnit.SECONDS);
    }

    private void sendAuth(WebSocket socket) {
        socket.sendText("{\"action\":\"auth\",\"params\":\"" + escapeJson(massiveApiKey) + "\"}", true);
    }

    private void subscribeAll(WebSocket socket) {
        List<String> symbols = new ArrayList<>(stockBySymbol.keySet());
        if (symbols.isEmpty()) {
            return;
        }

        for (int i = 0; i < symbols.size(); i += SUBSCRIPTION_CHUNK_SIZE) {
            int end = Math.min(i + SUBSCRIPTION_CHUNK_SIZE, symbols.size());
            String params = String.join(",", symbols.subList(i, end).stream().map(s -> "A." + s).toList());
            socket.sendText("{\"action\":\"subscribe\",\"params\":\"" + params + "\"}", true);
        }
        log.info("Subscribed websocket cache to {} symbols.", symbols.size());
    }

    private void handleMessage(String payload, WebSocket socket) {
        try {
            JsonNode events = OBJECT_MAPPER.readTree(payload);
            if (!events.isArray()) {
                return;
            }

            for (JsonNode event : events) {
                if (!event.isObject()) {
                    continue;
                }
                JsonNode evNode = event.get("ev");
                String ev = evNode != null && !evNode.isNull() ? evNode.textValue() : "";
                if ("status".equals(ev)) {
                    JsonNode statusNode = event.get("status");
                    String status = statusNode != null && !statusNode.isNull() ? statusNode.textValue() : "";
                    if ("connected".equals(status)) {
                        sendAuth(socket);
                    } else if ("auth_success".equals(status)) {
                        subscribeAll(socket);
                    } else if ("auth_failed".equals(status)) {
                        log.error("Massive websocket auth failed for all-stocks cache.");
                    }
                    continue;
                }

                if ("A".equals(ev)) {
                    upsertAggregate(event);
                }
            }
        } catch (Exception ex) {
            log.debug("Skipping websocket payload parse error: {}", ex.getMessage());
        }
    }

    private synchronized void upsertAggregate(JsonNode event) {
        upsertAggregate(event, true);
    }

    private synchronized void upsertAggregate(JsonNode event, boolean fromRealtimeWebSocket) {
        JsonNode symbolNode = event.get("sym");
        String symbol = normalizeSymbol(symbolNode != null && !symbolNode.isNull() ? symbolNode.textValue() : null);
        if (symbol == null) {
            return;
        }
        Stock stock = stockBySymbol.get(symbol);
        if (stock == null || stock.getStockId() == null) {
            return;
        }

        BigDecimal open = number(event.path("o"));
        BigDecimal close = number(event.path("c"));
        BigDecimal high = number(event.path("h"));
        BigDecimal low = number(event.path("l"));
        BigDecimal vwap = number(event.path("vw"));
        long volume = event.path("v").asLong(0L);
        long timestamp = event.path("e").asLong(System.currentTimeMillis());

        if (open == null || close == null || high == null || low == null) {
            return;
        }

        AllStocksLastValueCache entry = cacheByStockId.computeIfAbsent(stock.getStockId(), id -> {
            AllStocksLastValueCache created = new AllStocksLastValueCache();
            created.setStock(stock);
            return created;
        });

        entry.setCachedOpen(open);
        entry.setCachedClose(close);
        entry.setCachedHigh(high);
        entry.setCachedLow(low);
        entry.setCachedVolume(volume);
        entry.setCachedVwap(vwap != null ? vwap : close);
        entry.setCachedChangePercent(calculateChangePercent(open, close));
        entry.setAggregateUpdatedAt(Instant.ofEpochMilli(timestamp));

        // Save to database immediately (not batched) for true real-time data
        try {
            allStocksLastValueCacheRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Failed to save stock cache entry for stock_id {}: {}", stock.getStockId(), ex.getMessage());
        }

        if (fromRealtimeWebSocket) {
            lastRealtimeAggregateAt.set(Instant.now());
        }

        eventPublisher.publishEvent(new StockCacheUpdatedEvent(stock.getStockId()));
    }

    private void refreshFeaturedSnapshotFallback() {
        if (massiveApiKey == null || massiveApiKey.isBlank()) {
            return;
        }
        if (isRealtimeFeedFresh()) {
            return;
        }

        try {
            List<String> tickers = featuredStockCacheRepository.findAllByOrderBySortOrderAsc()
                    .stream()
                    .map(FeaturedStockCache::getStock)
                    .filter(stock -> stock != null && stock.getSymbol() != null && !stock.getSymbol().isBlank())
                    .limit(FEATURED_FALLBACK_LIMIT)
                    .map(Stock::getSymbol)
                    .map(this::normalizeSymbol)
                    .filter(symbol -> symbol != null && stockBySymbol.containsKey(symbol))
                    .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));

            if (tickers.isEmpty()) {
                return;
            }

            String uri = MASSIVE_API_BASE_URL
                    + "/v2/snapshot/locale/us/markets/stocks/tickers?tickers="
                    + String.join(",", tickers)
                    + "&apiKey="
                    + java.net.URLEncoder.encode(massiveApiKey, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Featured snapshot fallback returned HTTP {}.", response.statusCode());
                return;
            }

            JsonNode body = OBJECT_MAPPER.readTree(response.body());
            JsonNode tickersNode = body.path("tickers");
            if (!tickersNode.isArray()) {
                log.debug("Featured snapshot fallback response did not contain a tickers array.");
                return;
            }

            int updatedCount = 0;
            for (JsonNode tickerNode : tickersNode) {
                if (upsertAggregateSnapshot(tickerNode)) {
                    updatedCount++;
                }
            }

            if (updatedCount > 0) {
                log.info("Featured snapshot fallback refreshed {} symbols while websocket feed was stale.", updatedCount);
            }
        } catch (Exception ex) {
            log.warn("Featured snapshot fallback refresh failed: {}", ex.getMessage());
        }
    }

    private boolean upsertAggregateSnapshot(JsonNode tickerNode) {
        JsonNode dayNode = tickerNode.path("day");
        if (dayNode.isMissingNode() || dayNode.isNull()) {
            return false;
        }

        String symbol = normalizeSymbol(tickerNode.path("ticker").asText(null));
        if (symbol == null) {
            return false;
        }

        BigDecimal open = number(dayNode.path("o"));
        BigDecimal close = number(dayNode.path("c"));
        BigDecimal high = number(dayNode.path("h"));
        BigDecimal low = number(dayNode.path("l"));
        BigDecimal vwap = number(dayNode.path("vw"));
        JsonNode changePercentNode = tickerNode.get("todaysChangePerc");
        BigDecimal changePercent = number(changePercentNode);
        long volume = dayNode.path("v").asLong(0L);
        Instant updatedAt = parseSnapshotTimestamp(tickerNode.path("updated").asLong(0L));

        if (open == null || close == null || high == null || low == null) {
            return false;
        }

        JsonNode aggregateEvent = OBJECT_MAPPER.createObjectNode()
                .put("sym", symbol)
                .put("o", open.doubleValue())
                .put("c", close.doubleValue())
                .put("h", high.doubleValue())
                .put("l", low.doubleValue())
                .put("vw", (vwap != null ? vwap : close).doubleValue())
                .put("v", volume)
                .put("e", updatedAt.toEpochMilli());

        upsertAggregate(aggregateEvent, false);

        if (changePercent != null) {
            Stock stock = stockBySymbol.get(symbol);
            if (stock != null && stock.getStockId() != null) {
                AllStocksLastValueCache entry = cacheByStockId.get(stock.getStockId());
                if (entry != null) {
                    entry.setCachedChangePercent(changePercent.setScale(2, RoundingMode.HALF_UP));
                    try {
                        allStocksLastValueCacheRepository.save(entry);
                    } catch (Exception ex) {
                        log.debug("Unable to persist snapshot change percent for {}: {}", symbol, ex.getMessage());
                    }
                }
            }
        }

        return true;
    }

    private Instant parseSnapshotTimestamp(long rawTimestamp) {
        if (rawTimestamp <= 0L) {
            return Instant.now();
        }
        if (rawTimestamp >= 1_000_000_000_000_000_000L) {
            return Instant.ofEpochSecond(rawTimestamp / 1_000_000_000L, rawTimestamp % 1_000_000_000L);
        }
        if (rawTimestamp >= 1_000_000_000_000_000L) {
            long millis = rawTimestamp / 1_000_000L;
            return Instant.ofEpochMilli(millis);
        }
        if (rawTimestamp >= 1_000_000_000_000L) {
            return Instant.ofEpochMilli(rawTimestamp);
        }
        return Instant.ofEpochSecond(rawTimestamp);
    }

    private boolean isRealtimeFeedFresh() {
        Instant lastUpdate = lastRealtimeAggregateAt.get();
        return lastUpdate != null && lastUpdate.plusSeconds(WEBSOCKET_STALE_SECONDS).isAfter(Instant.now());
    }

    public Collection<AllStocksLastValueCache> getCacheSnapshotValues() {
        ensureCacheHydrated();
        return new ArrayList<>(cacheByStockId.values());
    }

    public AllStocksLastValueCache getCacheEntryByStockId(Long stockId) {
        ensureCacheHydrated();
        return stockId == null ? null : cacheByStockId.get(stockId);
    }

    public AllStocksLastValueCache getCacheEntryBySymbol(String symbol) {
        ensureCacheHydrated();
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return null;
        }
        Stock stock = stockBySymbol.get(normalized);
        return stock == null ? null : cacheByStockId.get(stock.getStockId());
    }

    private synchronized void ensureCacheHydrated() {
        if (stockBySymbol.isEmpty()) {
            loadStocks();
        }
        if (!cacheByStockId.isEmpty()) {
            return;
        }
        warmInMemoryCache();
        if (!cacheByStockId.isEmpty()) {
            log.info("Hydrated in-memory all-stocks cache with {} entries from database.", cacheByStockId.size());
        }
    }

    private BigDecimal number(JsonNode node) {
        return node != null && node.isNumber()
                ? BigDecimal.valueOf(node.asDouble()).setScale(2, RoundingMode.HALF_UP)
                : null;
    }

    private String normalizeSymbol(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private BigDecimal calculateChangePercent(BigDecimal openPrice, BigDecimal closePrice) {
        if (openPrice == null || closePrice == null || openPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return closePrice
                .subtract(openPrice)
                .divide(openPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").join();
            }
        } catch (Exception ignored) {
        }
        scheduler.shutdownNow();
    }

    private final class MassiveWebSocketListener implements WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            AllStocksLastValueCacheService.this.webSocket = webSocket;
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                handleMessage(textBuffer.toString(), webSocket);
                textBuffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("All-stocks websocket error: {}", error.getMessage());
            if (AllStocksLastValueCacheService.this.webSocket == webSocket) {
                AllStocksLastValueCacheService.this.webSocket = null;
            }
            queueReconnect();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("All-stocks websocket closed: {} {}", statusCode, reason);
            if (AllStocksLastValueCacheService.this.webSocket == webSocket) {
                AllStocksLastValueCacheService.this.webSocket = null;
            }
            queueReconnect();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }
    }
}


