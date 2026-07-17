package com.tradepulse.stockservice.service;

import com.tradepulse.stockservice.dto.stock.StockResponseDTO;
import com.tradepulse.stockservice.exception.StockNotFoundException;
import com.tradepulse.stockservice.mapper.StockMapper;
import com.tradepulse.stockservice.model.AllStocksLastValueCache;
import com.tradepulse.stockservice.model.Stock;
import com.tradepulse.stockservice.model.StockMarketData;
import com.tradepulse.stockservice.repository.StockMarketDataRepository;
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
    private final StockMarketDataRepository stockMarketDataRepository;
    private final FeaturedStockCacheRepository featuredStockCacheRepository;
    private final AllStocksLastValueCacheService allStocksLastValueCacheService;

    public StockService(StockRepository stockRepository,
                        StockMarketDataRepository stockMarketDataRepository,
                        FeaturedStockCacheRepository featuredStockCacheRepository,
                        AllStocksLastValueCacheService allStocksLastValueCacheService) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
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

        Map<Long, StockMarketData> latestByStockId = new HashMap<>();
        stockMarketDataRepository.findLatestForAllStocks()
                .forEach(data -> latestByStockId.put(data.getStock().getStockId(), data));

        return stockRepository.findAllByOrderByStockIdAsc()
                .stream()
                .filter(stock -> latestByStockId.containsKey(stock.getStockId()))
                .map(stock -> StockMapper.toDTO(stock, latestByStockId.get(stock.getStockId())))
                .toList();
    }

    public List<StockResponseDTO> getFeaturedStocks() {
        // Fetch top 50 from featured stocks cache ranking.
        var cachedFeaturedStocks = featuredStockCacheRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .limit(FEATURED_LIMIT)
                .toList();

        Map<Long, AllStocksLastValueCache> realtimeByStockId = new HashMap<>();
        allStocksLastValueCacheService.getCacheSnapshotValues()
                .forEach(entry -> realtimeByStockId.put(entry.getStock().getStockId(), entry));

        Map<Long, StockMarketData> latestByStockId = new HashMap<>();
        if (realtimeByStockId.isEmpty() || cachedFeaturedStocks.isEmpty()) {
            stockMarketDataRepository.findLatestForAllStocks()
                    .forEach(data -> latestByStockId.put(data.getStock().getStockId(), data));
        }

        if (cachedFeaturedStocks.isEmpty()) {
            // Fallback path: cache can be empty after cold restarts before daily ranking job runs.
            return stockRepository.findAllByOrderByStockIdAsc()
                    .stream()
                    .filter(stock -> latestByStockId.containsKey(stock.getStockId()))
                    .sorted(Comparator
                            .comparing(Stock::getMarketCap, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(stock -> {
                                String symbol = stock.getSymbol();
                                return symbol == null ? "" : symbol.toUpperCase(Locale.ROOT);
                            }))
                    .limit(FEATURED_LIMIT)
                    .map(stock -> {
                        AllStocksLastValueCache realtime = realtimeByStockId.get(stock.getStockId());
                        if (realtime != null) {
                            return StockMapper.toDTO(realtime);
                        }
                        return StockMapper.toDTO(stock, latestByStockId.get(stock.getStockId()));
                    })
                    .toList();
        }

        return cachedFeaturedStocks.stream()
                .map(cacheEntry -> {
                    AllStocksLastValueCache realtime = realtimeByStockId.get(cacheEntry.getStock().getStockId());
                    if (realtime != null) {
                        return StockMapper.toDTO(realtime);
                    }
                    StockMarketData latestData = latestByStockId.get(cacheEntry.getStock().getStockId());
                    return StockMapper.toDTO(cacheEntry.getStock(), latestData);
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

        StockMarketData latestMarketData = stockMarketDataRepository.findTopByStockOrderByTradingDateDesc(stock)
                .orElseThrow(() -> new StockNotFoundException("Stock quote not available yet for id: " + id));

        if (latestMarketData.getClosePrice() == null) {
            throw new StockNotFoundException("Stock quote not available yet for id: " + id);
        }

        return StockMapper.toDTO(stock, latestMarketData);
    }

    public StockResponseDTO getStockBySymbol(String symbol) {
        String normalized = symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);

        AllStocksLastValueCache fromRealtimeCache = allStocksLastValueCacheService.getCacheEntryBySymbol(normalized);
        if (fromRealtimeCache != null) {
            return StockMapper.toDTO(fromRealtimeCache);
        }


        Stock stock = stockRepository.findBySymbol(normalized)
                .orElseThrow(() -> new StockNotFoundException("Stock not found with symbol: " + symbol));

        StockMarketData latestMarketData = stockMarketDataRepository.findTopByStockOrderByTradingDateDesc(stock)
                .orElseThrow(() -> new StockNotFoundException("Stock quote not available yet for symbol: " + symbol));

        if (latestMarketData.getClosePrice() == null) {
            throw new StockNotFoundException("Stock quote not available yet for symbol: " + symbol);
        }

        return StockMapper.toDTO(stock, latestMarketData);
    }


    public List<StockResponseDTO> searchStocks(String query) {
        if (query == null || query.trim().isEmpty()) {
            // Empty query returns empty list
            return List.of();
        }

        String searchQuery = query.trim().toLowerCase();
        final int MAX_RESULTS = 50;

        // Get all stocks from realtime cache first
        Map<Long, AllStocksLastValueCache> realtimeByStockId = new HashMap<>();
        allStocksLastValueCacheService.getCacheSnapshotValues()
                .forEach(entry -> realtimeByStockId.put(entry.getStock().getStockId(), entry));

        return stockRepository.findAllByOrderByStockIdAsc()
                .stream()
                .filter(stock -> {
                    if (stock.getSymbol() == null || stock.getSymbol().trim().isEmpty()) {
                        return false;
                    }
                    String symbol = stock.getSymbol().toLowerCase();
                    String name = (stock.getName() != null ? stock.getName() : "").toLowerCase();
                    return symbol.contains(searchQuery) || name.contains(searchQuery);
                })
                .limit(MAX_RESULTS)
                .map(stock -> {
                    // Use realtime cache if available, otherwise return basic DTO
                    AllStocksLastValueCache realtime = realtimeByStockId.get(stock.getStockId());
                    if (realtime != null) {
                        return StockMapper.toDTO(realtime);
                    }
                    return StockMapper.toDTOFromCache(stock);
                })
                .toList();
    }
}
