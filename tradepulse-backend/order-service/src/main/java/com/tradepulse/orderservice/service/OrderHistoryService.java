package com.tradepulse.orderservice.service;

import com.tradepulse.orderservice.dto.order.OrderResponseDTO;
import com.tradepulse.orderservice.mapper.OrderMapper;
import com.tradepulse.orderservice.model.TradeOrder;
import com.tradepulse.orderservice.repository.TradeOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderHistoryService {

    private static final int ORDER_NUMBER_MIN = 1_000_000;
    private static final int ORDER_NUMBER_MAX = 9_999_999;
    private static final int ORDER_NUMBER_GENERATION_ATTEMPTS = 200;
    private static final long ORDER_CACHE_TTL_MS = 30_000;
    private static final int ORDER_CACHE_MAX_ENTRIES = 2_000;
    private static final int ORDER_PAGE_CACHE_MAX_ENTRIES = 4_000;

    private final TradeOrderRepository tradeOrderRepository;
    private final StockCatalogClient stockCatalogClient;
    private final ConcurrentMap<Long, CachedValue<List<OrderResponseDTO>>> ordersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<OrderPageCacheKey, CachedValue<Page<OrderResponseDTO>>> ordersPageCache = new ConcurrentHashMap<>();

    public OrderHistoryService(TradeOrderRepository tradeOrderRepository, StockCatalogClient stockCatalogClient) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.stockCatalogClient = stockCatalogClient;
    }

    @Transactional
    public TradeOrder saveCompletedOrder(TradeOrder order) {
        if (order.getOrderNumber() == null) {
            order.setOrderNumber(generateUniqueOrderNumber());
        }
        TradeOrder savedOrder = tradeOrderRepository.save(order);
        evictOrderCaches(savedOrder.getUserId());
        return savedOrder;
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getOrders(Long userId) {
        List<OrderResponseDTO> cached = getFromCache(ordersCache, userId);
        if (cached != null) {
            return cached;
        }

        List<TradeOrder> userOrders = tradeOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        userOrders.forEach(this::ensureOrderNumber);
        List<OrderResponseDTO> orders = mapOrders(userOrders);
        putIntoCache(ordersCache, userId, orders, ORDER_CACHE_TTL_MS, ORDER_CACHE_MAX_ENTRIES);
        return orders;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> getOrdersPage(Long userId, int page, int size) {
        OrderPageCacheKey cacheKey = new OrderPageCacheKey(userId, page, size);
        Page<OrderResponseDTO> cached = getFromCache(ordersPageCache, cacheKey);
        if (cached != null) {
            return cached;
        }

        Page<TradeOrder> userOrders = tradeOrderRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        userOrders.forEach(this::ensureOrderNumber);

        Page<OrderResponseDTO> ordersPage = userOrders.map(OrderMapper::toDTO);
        hydrateOrderSymbols(ordersPage.getContent());
        putIntoCache(ordersPageCache, cacheKey, ordersPage, ORDER_CACHE_TTL_MS, ORDER_PAGE_CACHE_MAX_ENTRIES);
        return ordersPage;
    }

    private List<OrderResponseDTO> mapOrders(List<TradeOrder> userOrders) {
        List<OrderResponseDTO> orders = userOrders
                .stream()
                .map(OrderMapper::toDTO)
                .toList();
        hydrateOrderSymbols(orders);
        return orders;
    }

    private void hydrateOrderSymbols(List<OrderResponseDTO> orders) {
        Map<Long, StockQuote> quotes = new LinkedHashMap<>();
        for (OrderResponseDTO order : orders) {
            order.getItems().forEach(item -> {
                Long stockId = Long.parseLong(item.getStockId());
                StockQuote quote = quotes.computeIfAbsent(stockId, stockCatalogClient::getStockQuote);
                item.setSymbol(quote.symbol());
            });
        }
    }

    private void evictOrderCaches(Long userId) {
        if (userId == null) {
            return;
        }
        ordersCache.remove(userId);
        ordersPageCache.entrySet().removeIf(entry -> entry.getKey().userId().equals(userId));
    }

    private <K, V> V getFromCache(ConcurrentMap<K, CachedValue<V>> cache, K key) {
        CachedValue<V> cached = cache.get(key);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAtEpochMs() < System.currentTimeMillis()) {
            cache.remove(key, cached);
            return null;
        }
        return cached.value();
    }

    private <K, V> void putIntoCache(ConcurrentMap<K, CachedValue<V>> cache, K key, V value, long ttlMs, int maxEntries) {
        evictExpiredEntries(cache);
        if (cache.size() >= maxEntries) {
            evictOldestEntry(cache);
        }
        cache.put(key, new CachedValue<>(value, System.currentTimeMillis() + ttlMs));
    }

    private <K, V> void evictExpiredEntries(ConcurrentMap<K, CachedValue<V>> cache) {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMs() < now);
    }

    private <K, V> void evictOldestEntry(ConcurrentMap<K, CachedValue<V>> cache) {
        K oldestKey = null;
        long oldestTimestamp = Long.MAX_VALUE;
        for (Map.Entry<K, CachedValue<V>> entry : cache.entrySet()) {
            long createdAt = entry.getValue().createdAtEpochMs();
            if (createdAt < oldestTimestamp) {
                oldestTimestamp = createdAt;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            cache.remove(oldestKey);
        }
    }

    private void ensureOrderNumber(TradeOrder order) {
        if (order.getOrderNumber() != null) {
            return;
        }
        order.setOrderNumber(generateUniqueOrderNumber());
        tradeOrderRepository.save(order);
    }

    private Integer generateUniqueOrderNumber() {
        for (int attempt = 0; attempt < ORDER_NUMBER_GENERATION_ATTEMPTS; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(ORDER_NUMBER_MIN, ORDER_NUMBER_MAX + 1);
            if (!tradeOrderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique 7-digit order number.");
    }

    private record OrderPageCacheKey(Long userId, int page, int size) {
    }

    private record CachedValue<T>(T value, long expiresAtEpochMs, long createdAtEpochMs) {
        private CachedValue(T value, long expiresAtEpochMs) {
            this(value, expiresAtEpochMs, System.currentTimeMillis());
        }
    }
}

