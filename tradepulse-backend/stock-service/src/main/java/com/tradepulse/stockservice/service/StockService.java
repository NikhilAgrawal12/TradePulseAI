package com.tradepulse.stockservice.service;

import com.tradepulse.stockservice.dto.stock.StockResponseDTO;
import com.tradepulse.stockservice.exception.StockNotFoundException;
import com.tradepulse.stockservice.mapper.StockMapper;
import com.tradepulse.stockservice.model.AllStocksLastValueCache;
import com.tradepulse.stockservice.model.Stock;
import com.tradepulse.stockservice.repository.StockRepository;
import com.tradepulse.stockservice.repository.FeaturedStockCacheRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StockService {

    private static final int FEATURED_LIMIT = 50;

    private final StockRepository stockRepository;
    private final FeaturedStockCacheRepository featuredStockCacheRepository;
    private final AllStocksLastValueCacheService allStocksLastValueCacheService;

    public StockService(StockRepository stockRepository,
                        FeaturedStockCacheRepository featuredStockCacheRepository,
                        AllStocksLastValueCacheService allStocksLastValueCacheService) {
        this.stockRepository = stockRepository;
        this.featuredStockCacheRepository = featuredStockCacheRepository;
        this.allStocksLastValueCacheService = allStocksLastValueCacheService;
    }

    public List<StockResponseDTO> getStocks() {
        List<AllStocksLastValueCache> fromRealtimeCache = allStocksLastValueCacheService.getCacheSnapshotValues().stream().toList();
        if (!fromRealtimeCache.isEmpty()) {
            return fromRealtimeCache.stream()
                    .sorted((a, b) -> Long.compare(a.getStock().getStockId(), b.getStock().getStockId()))
                    .map(StockMapper::toDTO)
                    .toList();
        }

        // Realtime cache not yet populated — return basic stock info without price
        return stockRepository.findAllByOrderByStockIdAsc()
                .stream()
                .map(StockMapper::toDTOFromCache)
                .toList();
    }

    public List<StockResponseDTO> getFeaturedStocks() {
        var cachedFeaturedStocks = featuredStockCacheRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .limit(FEATURED_LIMIT)
                .toList();

        Map<Long, AllStocksLastValueCache> realtimeByStockId = new HashMap<>();
        allStocksLastValueCacheService.getCacheSnapshotValues()
                .forEach(entry -> realtimeByStockId.put(entry.getStock().getStockId(), entry));

        if (cachedFeaturedStocks.isEmpty()) {
            // Fallback: cache empty on cold restart — return top stocks by market cap
            return stockRepository.findAllByOrderByStockIdAsc()
                    .stream()
                    .sorted(Comparator
                            .comparing(Stock::getMarketCap, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(stock -> {
                                String symbol = stock.getSymbol();
                                return symbol == null ? "" : symbol.toUpperCase(Locale.ROOT);
                            }))
                    .limit(FEATURED_LIMIT)
                    .map(stock -> {
                        AllStocksLastValueCache realtime = realtimeByStockId.get(stock.getStockId());
                        return realtime != null ? StockMapper.toDTO(realtime) : StockMapper.toDTOFromCache(stock);
                    })
                    .toList();
        }

        return cachedFeaturedStocks.stream()
                .map(cacheEntry -> {
                    AllStocksLastValueCache realtime = realtimeByStockId.get(cacheEntry.getStock().getStockId());
                    return realtime != null ? StockMapper.toDTO(realtime) : StockMapper.toDTOFromCache(cacheEntry.getStock());
                })
                .toList();
    }

    public Map<String, Object> getFeaturedCacheStatus() {
        long cachedCount = featuredStockCacheRepository.count();
        Map<String, Object> status = new HashMap<>();
        status.put("ready", cachedCount > 0);
        status.put("cachedCount", cachedCount);
        status.put("message", cachedCount > 0
                ? "Featured stocks cache (top 50) is ready"
                : "Featured stocks cache is empty — trigger POST /stocks/featured/refresh-once to populate");
        return status;
    }

    public StockResponseDTO getStockById(Long id) {
        AllStocksLastValueCache fromRealtimeCache = allStocksLastValueCacheService.getCacheEntryByStockId(id);
        if (fromRealtimeCache != null) {
            return StockMapper.toDTO(fromRealtimeCache);
        }

        Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new StockNotFoundException("Stock not found with id: " + id));

        return StockMapper.toDTOFromCache(stock);
    }

    public StockResponseDTO getStockBySymbol(String symbol) {
        String normalized = symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);

        AllStocksLastValueCache fromRealtimeCache = allStocksLastValueCacheService.getCacheEntryBySymbol(normalized);
        if (fromRealtimeCache != null) {
            return StockMapper.toDTO(fromRealtimeCache);
        }

        Stock stock = stockRepository.findBySymbol(normalized)
                .orElseThrow(() -> new StockNotFoundException("Stock not found with symbol: " + symbol));

        return StockMapper.toDTOFromCache(stock);
    }

    public List<StockResponseDTO> searchStocks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        String searchQuery = query.trim().toLowerCase();
        final int MAX_RESULTS = 50;

        Map<Long, AllStocksLastValueCache> realtimeByStockId = new HashMap<>();
        allStocksLastValueCacheService.getCacheSnapshotValues()
                .forEach(entry -> realtimeByStockId.put(entry.getStock().getStockId(), entry));

        return stockRepository.findAllByOrderByStockIdAsc()
                .stream()
                .filter(stock -> {
                    if (stock.getSymbol() == null || stock.getSymbol().trim().isEmpty()) {
                        return false;
                    }
                    String sym = stock.getSymbol().toLowerCase();
                    String name = (stock.getName() != null ? stock.getName() : "").toLowerCase();
                    return sym.contains(searchQuery) || name.contains(searchQuery);
                })
                .limit(MAX_RESULTS)
                .map(stock -> {
                    AllStocksLastValueCache realtime = realtimeByStockId.get(stock.getStockId());
                    return realtime != null ? StockMapper.toDTO(realtime) : StockMapper.toDTOFromCache(stock);
                })
                .toList();
    }
}
