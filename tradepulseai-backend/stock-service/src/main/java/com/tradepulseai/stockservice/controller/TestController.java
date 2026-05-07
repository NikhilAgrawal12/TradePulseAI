package com.tradepulseai.stockservice.controller;

import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import com.tradepulseai.stockservice.repository.StockMarketDataRepository;
import com.tradepulseai.stockservice.repository.StockRepository;
import com.tradepulseai.stockservice.service.PolygonHistoricalBackfillService;
import com.tradepulseai.stockservice.service.PolygonStockIngestionScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/test")
@Tag(name = "Testing", description = "Testing endpoints for development")
public class TestController {

    private final PolygonStockIngestionScheduler polygonStockIngestionScheduler;
    private final PolygonHistoricalBackfillService polygonHistoricalBackfillService;
    private final StockRepository stockRepository;
    private final StockMarketDataRepository stockMarketDataRepository;

    public TestController(
            PolygonStockIngestionScheduler polygonStockIngestionScheduler,
            PolygonHistoricalBackfillService polygonHistoricalBackfillService,
            StockRepository stockRepository,
            StockMarketDataRepository stockMarketDataRepository
    ) {
        this.polygonStockIngestionScheduler = polygonStockIngestionScheduler;
        this.polygonHistoricalBackfillService = polygonHistoricalBackfillService;
        this.stockRepository = stockRepository;
        this.stockMarketDataRepository = stockMarketDataRepository;
    }

    @PostMapping("/refresh/daily")
    @Operation(summary = "Trigger one direct daily refresh from Massive API to database")
    public ResponseEntity<String> refreshDaily() {
        polygonStockIngestionScheduler.fetchAndPersistDaily();
        return ResponseEntity.ok("✅ Direct daily refresh triggered: Massive API -> Database -> WebSocket");
    }

    @PostMapping("/refresh/history")
    @Operation(summary = "One-time: backfill recent grouped daily stock data window")
    public ResponseEntity<String> refreshHistory() {
        PolygonHistoricalBackfillService.BackfillResult result = polygonHistoricalBackfillService.runTwoYearBackfill();
        String body = "✅ Historical backfill finished\n"
                + "- Message: " + result.message() + "\n"
                + "- Requested market days: " + result.requestedDays() + "\n"
                + "- Successful API days: " + result.successfulDays() + "\n"
                + "- Persisted rows: " + result.persistedRows() + "\n"
                + "- Skipped duplicates: " + result.skippedRows();
        return ResponseEntity.ok(body);
    }

    @PostMapping("/websocket/rebroadcast-all")
    @Operation(summary = "Rebroadcast latest stored stock data to websocket clients")
    public ResponseEntity<String> publishAllStocks() {
        List<Stock> stocks = stockRepository.findAllByOrderByStockIdAsc();

        if (stocks.isEmpty()) {
            return ResponseEntity.badRequest().body("❌ No stock master records found in database");
        }

        int published = 0;
        for (Stock stock : stocks) {
            StockMarketData latestMarketData = stockMarketDataRepository
                    .findTopByStockOrderByMarketTimestampDesc(stock)
                    .orElse(null);

            if (latestMarketData == null || latestMarketData.getClosePrice() == null) {
                continue;
            }

            // no-op placeholder for backward-compatible endpoint; scheduler now broadcasts on direct persist
            published++;
        }

        return ResponseEntity.ok("✅ Found " + published + " stocks with latest market data\n" +
                "- Data is already in database\n" +
                "- WebSocket updates are pushed during direct daily refresh");
    }

    @GetMapping("/websocket/rebroadcast-all")
    @Operation(summary = "GET version - Rebroadcast ALL stocks")
    public ResponseEntity<String> publishAllStocksGet() {
        return publishAllStocks();
    }

    @GetMapping("/db/stocks-count")
    @Operation(summary = "Debug: Check database stock count and data")
    public ResponseEntity<String> countStocks() {
        long allStocks = stockRepository.count();
        long stocksWithPrice = stockMarketDataRepository.findLatestForAllStocks().size();

        StringBuilder sb = new StringBuilder();
        sb.append("Database Status:\n");
        sb.append("- Total stocks: ").append(allStocks).append("\n");
        sb.append("- Stocks with price data: ").append(stocksWithPrice).append("\n");

        if (stocksWithPrice > 0) {
            sb.append("\nStocks with data:\n");
            stockRepository.findAllByOrderByStockIdAsc().forEach(stock -> {
                StockMarketData latestMarketData = stockMarketDataRepository
                        .findTopByStockOrderByMarketTimestampDesc(stock)
                        .orElse(null);

                if (latestMarketData == null) {
                    return;
                }

                sb.append(String.format("  %s | Price: $%s | Change: %s%% | Volume: %s\n",
                    stock.getSymbol(),
                    latestMarketData.getClosePrice(),
                    latestMarketData.getChangePercent(),
                    latestMarketData.getVolume()));
            });
            sb.append("\n✅ Call /test/refresh/daily to fetch latest data directly from Massive API");
        } else {
            sb.append("\n❌ No market data found yet. Trigger /test/refresh/daily");
        }

        return ResponseEntity.ok(sb.toString());
    }

}

