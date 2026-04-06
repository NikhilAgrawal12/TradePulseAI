package com.tradepulseai.stockservice.controller;

import com.tradepulseai.stockservice.dto.StockResponseDTO;
import com.tradepulseai.stockservice.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/stocks")
@Tag(name = "Stocks", description = "API for reading stock market data")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    @Operation(summary = "Get all stocks")
    public ResponseEntity<List<StockResponseDTO>> getStocks() {
        return ResponseEntity.ok(stockService.getStocks());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get stock by id")
    public ResponseEntity<StockResponseDTO> getStockById(@PathVariable String id) {
        return ResponseEntity.ok(stockService.getStockById(id));
    }

    @GetMapping("/symbol/{symbol}")
    @Operation(summary = "Get stock by symbol")
    public ResponseEntity<StockResponseDTO> getStockBySymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(stockService.getStockBySymbol(symbol));
    }
}

