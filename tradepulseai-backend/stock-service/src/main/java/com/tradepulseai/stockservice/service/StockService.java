package com.tradepulseai.stockservice.service;

import com.tradepulseai.stockservice.dto.stock.StockResponseDTO;
import com.tradepulseai.stockservice.exception.StockNotFoundException;
import com.tradepulseai.stockservice.mapper.StockMapper;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.repository.StockRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public List<StockResponseDTO> getStocks() {
        return stockRepository.findAll(Sort.by(Sort.Direction.ASC, "stockId"))
                .stream()
                .map(StockMapper::toDTO)
                .toList();
    }

    public StockResponseDTO getStockById(Long id) {
        Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new StockNotFoundException("Stock not found with id: " + id));
        return StockMapper.toDTO(stock);
    }

    public StockResponseDTO getStockBySymbol(String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new StockNotFoundException("Stock not found with symbol: " + symbol));
        return StockMapper.toDTO(stock);
    }
}
