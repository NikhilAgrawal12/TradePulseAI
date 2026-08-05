package com.tradepulse.stockservice.controller;

import com.tradepulse.stockservice.dto.market.MarketStatusResponseDTO;
import com.tradepulse.stockservice.dto.stock.StockPredictionResponseDTO;
import com.tradepulse.stockservice.dto.stock.StockResponseDTO;
import com.tradepulse.stockservice.service.FeaturedStockRefreshService;
import com.tradepulse.stockservice.service.FeaturedStockSSEService;
import com.tradepulse.stockservice.service.MarketStatusCacheService;
import com.tradepulse.stockservice.service.MlPredictionService;
import com.tradepulse.stockservice.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stocks")
@Tag(name = "Stocks", description = "API for reading stock market data")
public class StockController {

    private final StockService stockService;
    private final FeaturedStockRefreshService featuredStockRefreshService;
    private final FeaturedStockSSEService featuredStockSSEService;
    private final MarketStatusCacheService marketStatusCacheService;
    private final MlPredictionService mlPredictionService;

    public StockController(StockService stockService,
                           FeaturedStockRefreshService featuredStockRefreshService,
                           FeaturedStockSSEService featuredStockSSEService,
                           MarketStatusCacheService marketStatusCacheService,
                           MlPredictionService mlPredictionService) {
        this.stockService = stockService;
        this.featuredStockRefreshService = featuredStockRefreshService;
        this.featuredStockSSEService = featuredStockSSEService;
        this.marketStatusCacheService = marketStatusCacheService;
        this.mlPredictionService = mlPredictionService;
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

    @PostMapping("/featured/refresh-once")
    @Operation(summary = "Manually populate featured cache once")
    public ResponseEntity<Map<String, Object>> refreshFeaturedStocksOnce() {
        featuredStockRefreshService.triggerManualRefresh();
        Map<String, Object> response = new HashMap<>();
        response.put("accepted", true);
        response.put("message", "Manual featured-stock refresh queued.");
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/featured/health")
    @Operation(summary = "Check if featured stocks cache is populated and ready")
    public ResponseEntity<Map<String, Object>> getFeaturedCacheHealth() {
        return ResponseEntity.ok(stockService.getFeaturedCacheStatus());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get stock by id")
    public ResponseEntity<StockResponseDTO> getStockById(@PathVariable Long id) {
        return ResponseEntity.ok(stockService.getStockById(id));
    }

    @GetMapping("/{id}/prediction")
    @Operation(summary = "Get ML buy/sell prediction for a stock")
    public ResponseEntity<StockPredictionResponseDTO> getStockPrediction(@PathVariable Long id) {
        return ResponseEntity.ok(mlPredictionService.getPredictionByStockId(id));
    }


    @GetMapping("/symbol/{symbol}")
    @Operation(summary = "Get stock by symbol")
    public ResponseEntity<StockResponseDTO> getStockBySymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(stockService.getStockBySymbol(symbol));
    }

    @GetMapping("/stream/featured")
    @Operation(summary = "Server-Sent Events stream for featured stocks + search results")
    public SseEmitter streamFeaturedStocks(@RequestParam(required = false) String query) {
        return featuredStockSSEService.subscribe(query);
    }

    @GetMapping("/search")
    @Operation(summary = "Search all 800 stocks in cache by symbol or name")
    public ResponseEntity<List<StockResponseDTO>> searchStocks(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(stockService.searchStocks(query));
    }

    @PostMapping("/stream/search")
    @Operation(summary = "Update search term for SSE client")
    public ResponseEntity<Map<String, Object>> updateStreamSearch(@RequestParam(required = false) String query) {
        Map<String, Object> response = new HashMap<>();
        response.put("acknowledged", true);
        response.put("searchQuery", query != null ? query : "");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stream/status")
    @Operation(summary = "Check SSE stream health")
    public ResponseEntity<Map<String, Object>> getStreamStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connectedClients", featuredStockSSEService.getConnectedClientsCount());
        status.put("streaming", true);
        return ResponseEntity.ok(status);
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
