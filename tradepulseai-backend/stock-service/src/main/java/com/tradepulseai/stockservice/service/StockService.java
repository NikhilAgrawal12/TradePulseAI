package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.stock.StockResponseDTO;
import com.tradepulseai.stockservice.exception.StockNotFoundException;
import com.tradepulseai.stockservice.mapper.StockMapper;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final StockMarketDataRepository stockMarketDataRepository;

    public StockService(StockRepository stockRepository, StockMarketDataRepository stockMarketDataRepository) {
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
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

    public StockResponseDTO getStockById(Long id) {
        Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new StockNotFoundException("Stock not found with id: " + id));

        StockMarketData latestMarketData = stockMarketDataRepository.findTopByStockOrderByMarketTimestampDesc(stock)
                .orElseThrow(() -> new StockNotFoundException("Stock quote not available yet for id: " + id));

        if (latestMarketData.getClosePrice() == null) {
            throw new StockNotFoundException("Stock quote not available yet for id: " + id);
        }

        return StockMapper.toDTO(stock, latestMarketData);
    }

    public StockResponseDTO getStockBySymbol(String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new StockNotFoundException("Stock not found with symbol: " + symbol));

        StockMarketData latestMarketData = stockMarketDataRepository.findTopByStockOrderByMarketTimestampDesc(stock)
                .orElseThrow(() -> new StockNotFoundException("Stock quote not available yet for symbol: " + symbol));

        if (latestMarketData.getClosePrice() == null) {
            throw new StockNotFoundException("Stock quote not available yet for symbol: " + symbol);
        }

        return StockMapper.toDTO(stock, latestMarketData);
    }
}
