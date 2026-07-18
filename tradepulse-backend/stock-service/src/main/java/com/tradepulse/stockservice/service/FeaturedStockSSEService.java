package com.tradepulse.stockservice.service;

import com.tradepulse.stockservice.dto.stock.StockResponseDTO;
import com.tradepulse.stockservice.mapper.StockMapper;
import com.tradepulse.stockservice.model.AllStocksLastValueCache;
import com.tradepulse.stockservice.model.StockMarketData;
import com.tradepulse.stockservice.repository.FeaturedStockCacheRepository;
import com.tradepulse.stockservice.repository.StockMarketDataRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service that manages Server-Sent Events (SSE) connections for real-time
 * featured stock updates. Streams featured stocks + search results to connected clients.
 */
@Service
public class FeaturedStockSSEService {

    private static final Logger log = LoggerFactory.getLogger(FeaturedStockSSEService.class);
    private static final long SSE_TIMEOUT = 5 * 60 * 1000; // 5 minutes
    private static final int SSE_RECONNECT_MS = 3000;
    private static final int EVENT_COALESCE_MS = 150;
    private static final int SEARCH_RESULT_LIMIT = 50;
    private static final String ALL_STOCKS_STREAM_QUERY = "__all__";

    private final FeaturedStockCacheRepository featuredStockCacheRepository;
    private final AllStocksLastValueCacheService allStocksLastValueCacheService;
    private final StockMarketDataRepository stockMarketDataRepository;
    private final ScheduledExecutorService eventBroadcastExecutor;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicBoolean broadcastQueued = new AtomicBoolean(false);

    // Keyed by emitter hashCode to track search terms per client
    private final Map<Integer, String> emitterSearchTerms = new ConcurrentHashMap<>();

    public FeaturedStockSSEService(
            FeaturedStockCacheRepository featuredStockCacheRepository,
            AllStocksLastValueCacheService allStocksLastValueCacheService,
            StockMarketDataRepository stockMarketDataRepository) {
        this.featuredStockCacheRepository = featuredStockCacheRepository;
        this.allStocksLastValueCacheService = allStocksLastValueCacheService;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.eventBroadcastExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "featured-stock-sse-event");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Register a new SSE client connection. The emitter will receive featured stock updates.
     * Sends cached featured stocks immediately on connection (no loading delay).
     */
    public SseEmitter subscribe(String searchTerm) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.add(emitter);
        int emitterId = emitter.hashCode();

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            emitterSearchTerms.put(emitterId, searchTerm.trim().toLowerCase());
        }

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            emitterSearchTerms.remove(emitterId);
            log.debug("SSE client disconnected (completed). Active emitters: {}", emitters.size());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitterSearchTerms.remove(emitterId);
            log.debug("SSE client timeout. Active emitters: {}", emitters.size());
        });

        emitter.onError(throwable -> {
            emitters.remove(emitter);
            emitterSearchTerms.remove(emitterId);
            log.debug("SSE client error: {}. Active emitters: {}", throwable.getMessage(), emitters.size());
        });

        // Send cached featured stocks immediately on connection (no wait for first broadcast)
        try {
            sendInitialCachedData(emitter, searchTerm);
        } catch (IOException ex) {
            emitters.remove(emitter);
            emitterSearchTerms.remove(emitterId);
            log.warn("Failed to send initial cached data to new client (IO error): {}", ex.getMessage());
        } catch (Exception ex) {
            emitters.remove(emitter);
            emitterSearchTerms.remove(emitterId);
            log.warn("Failed to send initial cached data to new client (general error): {}", ex.getMessage(), ex);
        }

        log.debug("New SSE client subscribed. Total emitters: {}", emitters.size());
        return emitter;
    }

    /**
     * Send cached featured stocks immediately to a newly connected client.
     * This eliminates "fetching" delay - users see market close data instantly.
     */
    private void sendInitialCachedData(SseEmitter emitter, String searchTerm) throws IOException {
        Map<Long, AllStocksLastValueCache> realtimeByStockId = new HashMap<>();
        try {
            allStocksLastValueCacheService.getCacheSnapshotValues()
                    .forEach(entry -> realtimeByStockId.put(entry.getStock().getStockId(), entry));
        } catch (Exception ex) {
            log.debug("Failed to get realtime cache snapshot: {}", ex.getMessage());
        }

        Map<Long, StockMarketData> latestByStockId = new HashMap<>();
        if (realtimeByStockId.isEmpty()) {
            try {
                stockMarketDataRepository.findLatestForAllStocks()
                        .forEach(data -> latestByStockId.put(data.getStock().getStockId(), data));
            } catch (Exception ex) {
                log.debug("Failed to query latest stock market data: {}", ex.getMessage());
            }
        }

        List<StockResponseDTO> initialData;
        try {
            initialData = featuredStockCacheRepository.findAllByOrderBySortOrderAsc()
                    .stream()
                    .limit(50)
                    .map(cache -> {
                        AllStocksLastValueCache realtime = realtimeByStockId.get(cache.getStock().getStockId());
                        if (realtime != null) {
                            return StockMapper.toDTO(realtime);
                        }
                        StockMarketData latestData = latestByStockId.get(cache.getStock().getStockId());
                        return StockMapper.toDTO(cache.getStock(), latestData);
                    })
                    .toList();
        } catch (Exception ex) {
            log.debug("Failed to query featured stocks cache: {}", ex.getMessage());
            initialData = new ArrayList<>();
        }

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String normalizedSearch = searchTerm.trim().toLowerCase();
            if (isAllStocksQuery(normalizedSearch)) {
                initialData = realtimeByStockId.values()
                        .stream()
                        .sorted(Comparator.comparing(entry -> entry.getStock().getStockId()))
                        .map(StockMapper::toDTO)
                        .toList();
            } else {
            initialData = realtimeByStockId.values()
                    .stream()
                    .map(StockMapper::toDTO)
                    .filter(stock -> {
                        if (stock.getSymbol() == null || stock.getSymbol().trim().isEmpty()) {
                            return false;
                        }
                        String symbol = stock.getSymbol().toLowerCase();
                        String name = (stock.getName() != null ? stock.getName() : "").toLowerCase();
                        return symbol.contains(normalizedSearch) || name.contains(normalizedSearch);
                    })
                    .limit(SEARCH_RESULT_LIMIT)
                    .toList();
            }
        } else if (initialData.isEmpty()) {
            initialData = buildFallbackFeaturedFromRealtime(realtimeByStockId);
        }

        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .id(System.currentTimeMillis() + "_initial")
                .name("stocks")
                .data(initialData)
                .reconnectTime(SSE_RECONNECT_MS);

        emitter.send(event);
    }

    @EventListener
    public void onStockCacheUpdated(StockCacheUpdatedEvent ignoredEvent) {
        if (emitters.isEmpty()) {
            return;
        }

        if (!broadcastQueued.compareAndSet(false, true)) {
            return;
        }

        eventBroadcastExecutor.schedule(() -> {
            try {
                broadcastToAllClients();
            } finally {
                broadcastQueued.set(false);
            }
        }, EVENT_COALESCE_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Broadcasts current featured stocks (+ search results if applicable) to all connected SSE clients.
     */
    private void broadcastToAllClients() {
        if (emitters.isEmpty()) {
            return;
        }

        try {
            // Get latest realtime data for all stocks
            Map<Long, AllStocksLastValueCache> realtimeByStockId = new HashMap<>();
            allStocksLastValueCacheService.getCacheSnapshotValues()
                    .forEach(entry -> realtimeByStockId.put(entry.getStock().getStockId(), entry));

            Map<Long, StockMarketData> latestByStockId = new HashMap<>();
            if (realtimeByStockId.isEmpty()) {
                stockMarketDataRepository.findLatestForAllStocks()
                        .forEach(data -> latestByStockId.put(data.getStock().getStockId(), data));
            }

            // Get featured stocks from cache (top 50)
            List<StockResponseDTO> featuredStocks = featuredStockCacheRepository.findAllByOrderBySortOrderAsc()
                    .stream()
                    .limit(50)
                    .map(cache -> {
                        AllStocksLastValueCache realtime = realtimeByStockId.get(cache.getStock().getStockId());
                        if (realtime != null) {
                            return StockMapper.toDTO(realtime);
                        }
                        StockMarketData latestData = latestByStockId.get(cache.getStock().getStockId());
                        return StockMapper.toDTO(cache.getStock(), latestData);
                    })
                    .toList();

            if (featuredStocks.isEmpty()) {
                featuredStocks = buildFallbackFeaturedFromRealtime(realtimeByStockId);
            }

            // Broadcast to each connected client
            for (SseEmitter emitter : emitters) {
                broadcastToClient(emitter, featuredStocks, realtimeByStockId);
            }
        } catch (Exception ex) {
            log.debug("Error during broadcast cycle: {}", ex.getMessage());
        }
    }

    /**
     * Broadcast data to a specific SSE client.
     * Sends featured stocks or search results based on the client's search term.
     */
    private void broadcastToClient(SseEmitter emitter, List<StockResponseDTO> featuredStocks,
            Map<Long, AllStocksLastValueCache> realtimeByStockId) {
        try {
            Integer emitterId = emitter.hashCode();
            String searchTerm = emitterSearchTerms.get(emitterId);

            List<StockResponseDTO> toSend;

            if (searchTerm != null && !searchTerm.isEmpty()) {
                // Search mode: filter all stocks and return top 50 matches
                if (isAllStocksQuery(searchTerm)) {
                    toSend = realtimeByStockId.values()
                            .stream()
                            .sorted(Comparator.comparing(entry -> entry.getStock().getStockId()))
                            .map(StockMapper::toDTO)
                            .toList();
                } else {
                    toSend = realtimeByStockId.values()
                            .stream()
                            .map(StockMapper::toDTO)
                            .filter(stock -> {
                                if (stock.getSymbol() == null || stock.getSymbol().trim().isEmpty()) {
                                    return false;
                                }
                                String symbol = stock.getSymbol().toLowerCase();
                                String name = (stock.getName() != null ? stock.getName() : "").toLowerCase();
                                return symbol.contains(searchTerm) || name.contains(searchTerm);
                            })
                            .limit(SEARCH_RESULT_LIMIT)
                            .toList();
                }
            } else {
                // No search: send featured stocks only
                toSend = featuredStocks;
            }

            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .id(System.currentTimeMillis() + "_" + emitterId)
                    .name("stocks")
                    .data(toSend)
                    .reconnectTime(SSE_RECONNECT_MS);

            emitter.send(event);
        } catch (IOException ex) {
            // Client disconnected, it will be removed by the onError/onCompletion callback
            emitters.remove(emitter);
            emitterSearchTerms.remove(emitter.hashCode());
        }
    }

    public int getConnectedClientsCount() {
        return emitters.size();
    }

    private boolean isAllStocksQuery(String searchTerm) {
        return ALL_STOCKS_STREAM_QUERY.equals(searchTerm);
    }

    private List<StockResponseDTO> buildFallbackFeaturedFromRealtime(Map<Long, AllStocksLastValueCache> realtimeByStockId) {
        return realtimeByStockId.values()
                .stream()
                .sorted(Comparator
                        .comparing((AllStocksLastValueCache entry) -> entry.getStock().getMarketCap(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(entry -> {
                            String symbol = entry.getStock().getSymbol();
                            return symbol == null ? "" : symbol.toUpperCase(Locale.ROOT);
                        }))
                .limit(50)
                .map(StockMapper::toDTO)
                .toList();
    }

    @PreDestroy
    public void shutdown() {
        eventBroadcastExecutor.shutdownNow();
    }
}

