package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.model.AllStocksLastValueCache;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.repository.AllStocksLastValueCacheRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Order(5)
public class AllStocksLastValueCacheService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AllStocksLastValueCacheService.class);
    private static final String MASSIVE_DELAYED_WS_URL = "wss://delayed.massive.com/stocks";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int SUBSCRIPTION_CHUNK_SIZE = 200;

    private final StockRepository stockRepository;
    private final AllStocksLastValueCacheRepository allStocksLastValueCacheRepository;
    private final ApplicationEventPublisher eventPublisher;
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

    public AllStocksLastValueCacheService(
            StockRepository stockRepository,
            AllStocksLastValueCacheRepository allStocksLastValueCacheRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${massive.api.key:}") String massiveApiKey) {
        this.stockRepository = stockRepository;
        this.allStocksLastValueCacheRepository = allStocksLastValueCacheRepository;
        this.eventPublisher = eventPublisher;
        this.massiveApiKey = massiveApiKey;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (massiveApiKey == null || massiveApiKey.isBlank()) {
            log.warn("All-stocks websocket cache disabled because massive.api.key is missing.");
            return;
        }

        loadStocks();
        warmInMemoryCache();
        connect();
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
        for (AllStocksLastValueCache entry : allStocksLastValueCacheRepository.findAll()) {
            if (entry.getStock() != null && entry.getStock().getStockId() != null) {
                cacheByStockId.put(entry.getStock().getStockId(), entry);
            }
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

        eventPublisher.publishEvent(new StockCacheUpdatedEvent(stock.getStockId()));
    }

    public Collection<AllStocksLastValueCache> getCacheSnapshotValues() {
        return new ArrayList<>(cacheByStockId.values());
    }

    public AllStocksLastValueCache getCacheEntryByStockId(Long stockId) {
        return stockId == null ? null : cacheByStockId.get(stockId);
    }

    public AllStocksLastValueCache getCacheEntryBySymbol(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return null;
        }
        Stock stock = stockBySymbol.get(normalized);
        return stock == null ? null : cacheByStockId.get(stock.getStockId());
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


