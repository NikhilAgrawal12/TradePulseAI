package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.stock.StockResponseDTO;
import com.tradepulseai.stockservice.exception.StockNotFoundException;
import com.tradepulseai.stockservice.mapper.StockMapper;
import com.tradepulseai.stockservice.model.AllStocksLastValueCache;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.repository.AllStocksLastValueCacheRepository;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import com.tradepulseai.stockservice.repository.FeaturedStockCacheRepository;
import org.springframework.stereotype.Service;

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
    private final AllStocksLastValueCacheRepository allStocksLastValueCacheRepository;

    public StockService(StockRepository stockRepository,
                        StockMarketDataRepository stockMarketDataRepository,
                        FeaturedStockCacheRepository featuredStockCacheRepository,
                        AllStocksLastValueCacheRepository allStocksLastValueCacheRepository) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.featuredStockCacheRepository = featuredStockCacheRepository;
        this.allStocksLastValueCacheRepository = allStocksLastValueCacheRepository;
    }

    public List<StockResponseDTO> getStocks() {
        List<AllStocksLastValueCache> fromRealtimeCache = allStocksLastValueCacheRepository.findAll();
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

        if (cachedFeaturedStocks.isEmpty()) {
            // If cache is empty, return empty list (frontend will handle gracefully)
            return List.of();
        }

        Map<Long, AllStocksLastValueCache> realtimeByStockId = new HashMap<>();
        allStocksLastValueCacheRepository.findAll()
                .forEach(entry -> realtimeByStockId.put(entry.getStock().getStockId(), entry));

        Map<Long, StockMarketData> latestByStockId = new HashMap<>();
        if (realtimeByStockId.isEmpty()) {
            stockMarketDataRepository.findLatestForAllStocks()
                    .forEach(data -> latestByStockId.put(data.getStock().getStockId(), data));
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
        AllStocksLastValueCache fromRealtimeCache = allStocksLastValueCacheRepository.findByStockStockId(id).orElse(null);
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

        AllStocksLastValueCache fromRealtimeCache = allStocksLastValueCacheRepository.findByStockSymbolIgnoreCase(normalized).orElse(null);
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
}
