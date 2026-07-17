package com.tradepulse.stockservice.controller;

import com.tradepulse.stockservice.dto.market.MarketStatusResponseDTO;
import com.tradepulse.stockservice.dto.stock.AnalyticsNewsItemDTO;
import com.tradepulse.stockservice.dto.stock.StockInsightsResponseDTO;
import com.tradepulse.stockservice.dto.stock.StockPredictionResponseDTO;
import com.tradepulse.stockservice.dto.stock.StockResponseDTO;
import com.tradepulse.stockservice.service.FeaturedStockRefreshService;
import com.tradepulse.stockservice.service.FeaturedStockSSEService;
import com.tradepulse.stockservice.service.NewsIntegrationScheduler;
import com.tradepulse.stockservice.service.MarketStatusCacheService;
import com.tradepulse.stockservice.service.StockInsightsService;
import com.tradepulse.stockservice.service.MlPredictionService;
import com.tradepulse.stockservice.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;

@RestController
@RequestMapping("/stocks")
@Tag(name = "Stocks", description = "API for reading stock market data")
public class StockController {

    private final StockService stockService;
    private final FeaturedStockRefreshService featuredStockRefreshService;
    private final FeaturedStockSSEService featuredStockSSEService;
    private final NewsIntegrationScheduler newsIntegrationScheduler;
    private final MarketStatusCacheService marketStatusCacheService;
    private final StockInsightsService stockInsightsService;
    private final MlPredictionService mlPredictionService;

    public StockController(StockService stockService,
                          FeaturedStockRefreshService featuredStockRefreshService,
                          FeaturedStockSSEService featuredStockSSEService,
                           NewsIntegrationScheduler newsIntegrationScheduler,
                          MarketStatusCacheService marketStatusCacheService,
                          StockInsightsService stockInsightsService,
                          MlPredictionService mlPredictionService) {
        this.stockService = stockService;
        this.featuredStockRefreshService = featuredStockRefreshService;
        this.featuredStockSSEService = featuredStockSSEService;
        this.newsIntegrationScheduler = newsIntegrationScheduler;
        this.marketStatusCacheService = marketStatusCacheService;
        this.stockInsightsService = stockInsightsService;
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

    @PostMapping("/admin/backfill-news")
    @Operation(summary = "Backfill news and sentiment for a stock over the last N trading days")
    public ResponseEntity<Map<String, Object>> backfillNews(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "365") int daysBack) {
        boolean accepted = newsIntegrationScheduler.triggerBackfillNewsForStock(ticker, daysBack);

        Map<String, Object> response = new HashMap<>();
        response.put("accepted", accepted);
        response.put("message", accepted
                ? "Historical news backfill queued."
                : "Another news backfill job is already running.");
        response.put("ticker", ticker);
        response.put("daysBack", daysBack);
        return accepted ? ResponseEntity.accepted().body(response) : ResponseEntity.status(409).body(response);
    }

    @PostMapping("/admin/backfill-news/all")
    @Operation(summary = "Backfill news and sentiment for all stocks over the last N trading days")
    public ResponseEntity<Map<String, Object>> backfillNewsForAllStocks(
            @RequestParam(defaultValue = "365") int daysBack) {
        boolean accepted = newsIntegrationScheduler.triggerBackfillNewsForAllStocks(daysBack);

        Map<String, Object> response = new HashMap<>();
        response.put("accepted", accepted);
        response.put("message", accepted
                ? "Historical news backfill queued for all stocks."
                : "Another news backfill job is already running.");
        response.put("daysBack", daysBack);
        return accepted ? ResponseEntity.accepted().body(response) : ResponseEntity.status(409).body(response);
    }

    @PostMapping("/admin/backfill-news/top")
    @Operation(summary = "Backfill news and sentiment for top N stocks by market cap over the last N calendar days")
    public ResponseEntity<Map<String, Object>> backfillNewsForTopStocks(
            @RequestParam(defaultValue = "365") int daysBack,
            @RequestParam(defaultValue = "200") int stockLimit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate resumeFromDate) {
        boolean accepted = newsIntegrationScheduler.triggerBackfillNewsForTopStocks(daysBack, stockLimit, resumeFromDate);

        Map<String, Object> response = new HashMap<>();
        response.put("accepted", accepted);
        response.put("message", accepted
                ? "Historical news backfill queued for top stocks by market cap."
                : "Another news backfill job is already running.");
        response.put("daysBack", daysBack);
        response.put("stockLimit", stockLimit);
        response.put("resumeFromDate", resumeFromDate == null ? null : resumeFromDate.toString());
        return accepted ? ResponseEntity.accepted().body(response) : ResponseEntity.status(409).body(response);
    }

    @GetMapping("/admin/backfill-news/status")
    @Operation(summary = "Get current or most recent historical news backfill status")
    public ResponseEntity<Map<String, Object>> getNewsBackfillStatus() {
        return ResponseEntity.ok(newsIntegrationScheduler.getBackfillStatus());
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


    @GetMapping("/{id}/insights")
    @Operation(summary = "Get detailed analytics and chart-ready insights for a stock")
    public ResponseEntity<StockInsightsResponseDTO> getStockInsights(@PathVariable Long id) {
        return ResponseEntity.ok(stockInsightsService.getInsights(id));
    }

    @GetMapping("/analytics/news")
    @Operation(summary = "Get latest daily news entries for analytics page")
    public ResponseEntity<List<AnalyticsNewsItemDTO>> getAnalyticsNews(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(stockInsightsService.getLatestMarketNews(limit));
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
        // This endpoint allows clients to signal their current search query
        // The actual search is handled server-side in the SSE stream
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
