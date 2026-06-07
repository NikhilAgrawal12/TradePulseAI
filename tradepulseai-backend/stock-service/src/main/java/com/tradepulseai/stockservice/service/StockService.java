package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.stock.StockResponseDTO;
import com.tradepulseai.stockservice.exception.StockNotFoundException;
import com.tradepulseai.stockservice.mapper.StockMapper;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import com.tradepulseai.stockservice.repository.FeaturedStockCacheRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final StockMarketDataRepository stockMarketDataRepository;
    private final FeaturedStockCacheRepository featuredStockCacheRepository;

    public StockService(StockRepository stockRepository,
                        StockMarketDataRepository stockMarketDataRepository,
                        FeaturedStockCacheRepository featuredStockCacheRepository) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
        this.featuredStockCacheRepository = featuredStockCacheRepository;
    }

    public List<StockResponseDTO> getStocks() {
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
        // Fetch featured stocks from cache (top 50 ranking)
        var cachedFeaturedStocks = featuredStockCacheRepository.findAllByOrderBySortOrderAsc();

        if (cachedFeaturedStocks.isEmpty()) {
            // If cache is empty, return empty list (frontend will handle gracefully)
            return List.of();
        }

        // Fetch latest market data for all stocks
        Map<Long, StockMarketData> latestByStockId = new HashMap<>();
        stockMarketDataRepository.findLatestForAllStocks()
                .forEach(data -> latestByStockId.put(data.getStock().getStockId(), data));

        // Map cached entries to DTOs with latest market data
        return cachedFeaturedStocks.stream()
                .map(cacheEntry -> StockMapper.toDTO(cacheEntry.getStock(),
                        latestByStockId.get(cacheEntry.getStock().getStockId())))
                .toList();
    }

    public Map<String, Object> getFeaturedCacheStatus() {
        long cachedCount = featuredStockCacheRepository.count();
        Map<String, Object> status = new HashMap<>();
        status.put("ready", cachedCount > 0);
        status.put("cachedCount", cachedCount);
        status.put("message", cachedCount > 0
                ? "Featured stocks cache is ready"
                : "Featured stocks cache is empty — trigger POST /stocks/featured/refresh-once to populate");
        return status;
    }

    public StockResponseDTO getStockById(Long id) {
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
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new StockNotFoundException("Stock not found with symbol: " + symbol));

        StockMarketData latestMarketData = stockMarketDataRepository.findTopByStockOrderByTradingDateDesc(stock)
                .orElseThrow(() -> new StockNotFoundException("Stock quote not available yet for symbol: " + symbol));

        if (latestMarketData.getClosePrice() == null) {
            throw new StockNotFoundException("Stock quote not available yet for symbol: " + symbol);
        }

        return StockMapper.toDTO(stock, latestMarketData);
    }
}
