package com.tradepulse.stockservice.controller;

import com.tradepulse.stockservice.dto.market.MarketStatusResponseDTO;
import com.tradepulse.stockservice.dto.stock.StockResponseDTO;
import com.tradepulse.stockservice.service.FeaturedStockSSEService;
import com.tradepulse.stockservice.service.MarketStatusCacheService;
import com.tradepulse.stockservice.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/stocks")
@Tag(name = "Stocks", description = "API for reading stock market data")
public class StockController {

    private final StockService stockService;
    private final FeaturedStockSSEService featuredStockSSEService;
    private final MarketStatusCacheService marketStatusCacheService;

    public StockController(StockService stockService,
                           FeaturedStockSSEService featuredStockSSEService,
                           MarketStatusCacheService marketStatusCacheService) {
        this.stockService = stockService;
        this.featuredStockSSEService = featuredStockSSEService;
        this.marketStatusCacheService = marketStatusCacheService;
    }

    @GetMapping
    @Operation(summary = "Get all stocks")
    public ResponseEntity<List<StockResponseDTO>> getStocks() {
        return ResponseEntity.ok(stockService.getStocks());
    }

    @GetMapping("/featured")
    @Operation(summary = "Get top 50 featured stocks ordered by sort_order")
    public ResponseEntity<List<StockResponseDTO>> getFeaturedStocks() {
        return ResponseEntity.ok(stockService.getFeaturedStocks());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get stock by id")
    public ResponseEntity<StockResponseDTO> getStockById(@PathVariable Long id) {
        return ResponseEntity.ok(stockService.getStockById(id));
    }


    @GetMapping("/stream/featured")
    @Operation(summary = "Server-Sent Events stream for featured stocks + search results")
    public SseEmitter streamFeaturedStocks(@RequestParam(required = false) String query) {
        return featuredStockSSEService.subscribe(query);
    }

    @GetMapping("/search")
    @Operation(summary = "Search stocks in cache by symbol or name")
    public ResponseEntity<List<StockResponseDTO>> searchStocks(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(stockService.searchStocks(query));
    }

    @GetMapping("/market-status")
    @Operation(summary = "Get backend-cached market session status")
    public ResponseEntity<MarketStatusResponseDTO> getMarketStatus() {
        return ResponseEntity.ok(marketStatusCacheService.getCurrentStatus());
    }

    @GetMapping("/stream/market-status")
    @Operation(summary = "Server-Sent Events stream for market session status")
    public SseEmitter streamMarketStatus() {
        return marketStatusCacheService.subscribe();
    }
}
