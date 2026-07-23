package com.tradepulse.stockservice.service;

import com.tradepulse.stockservice.dto.stock.StockInsightsResponseDTO;
import com.tradepulse.stockservice.model.AllStocksLastValueCache;
import com.tradepulse.stockservice.model.Stock;
import com.tradepulse.stockservice.model.StockMarketData;
import com.tradepulse.stockservice.model.StockMetrics;
import com.tradepulse.stockservice.repository.StockMarketDataRepository;
import com.tradepulse.stockservice.repository.StockMetricsRepository;
import com.tradepulse.stockservice.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockInsightsServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockMarketDataRepository stockMarketDataRepository;

    @Mock
    private StockMetricsRepository stockMetricsRepository;

    @Mock
    private AllStocksLastValueCacheService allStocksLastValueCacheService;

    @InjectMocks
    private StockInsightsService stockInsightsService;

    @Test
    void currentPerformanceUsesRealtimeCacheWhenAvailable() {
        Long stockId = 1L;
        Stock stock = stock(stockId);

        StockMarketData previousDay = marketData(LocalDate.of(2026, 6, 17), "100.00", 900_000L);
        StockMarketData latestDay = marketData(LocalDate.of(2026, 6, 18), "102.00", 1_000_000L);

        // Realtime cache has live intraday price
        AllStocksLastValueCache realtime = new AllStocksLastValueCache();
        realtime.setCachedClose(BigDecimal.valueOf(103.50));
        realtime.setCachedChangePercent(BigDecimal.valueOf(1.47));
        realtime.setAggregateUpdatedAt(Instant.parse("2026-06-19T14:30:00Z"));

        when(stockRepository.findById(stockId)).thenReturn(Optional.of(stock));
        when(stockMetricsRepository.findById(stockId)).thenReturn(Optional.empty());
        when(stockMarketDataRepository.findRecentByStockId(eq(stockId), any(Pageable.class)))
                .thenReturn(List.of(latestDay, previousDay));
        when(allStocksLastValueCacheService.getCacheEntryByStockId(stockId)).thenReturn(realtime);

        StockInsightsResponseDTO response = stockInsightsService.getInsights(stockId);

        // Current price comes from realtime cache
        assertThat(response.currentPerformance().currentPrice()).isEqualTo(103.5d);
        // Previous close is latest daily OHLC close (yesterday's close relative to intraday price)
        assertThat(response.currentPerformance().previousClose()).isEqualTo(102.0d);
        // Daily change % comes from realtime cache
        assertThat(response.currentPerformance().dailyChangePercent()).isEqualTo(1.47d);
        // Volume uses daily OHLC — not realtime aggregate
        assertThat(response.volumeMetrics().latestTradingDayVolume()).isEqualTo(1_000_000L);
        assertThat(response.volumeMetrics().latestTradingDate()).isEqualTo("2026-06-18");
    }

    @Test
    void technicalIndicatorsUseLatestOhlc() {
        Long stockId = 1L;
        Stock stock = stock(stockId);

        StockMarketData previousDay = marketData(LocalDate.of(2026, 6, 17), "100.00", 900_000L);
        StockMarketData latestDay = marketData(LocalDate.of(2026, 6, 18), "102.00", 1_000_000L);
        latestDay.setVolatility90d(new BigDecimal("22.22"));
        latestDay.setRsi14(new BigDecimal("55.55"));
        latestDay.setMacd(new BigDecimal("1.23"));
        latestDay.setMacdSignal(new BigDecimal("0.98"));

        StockMetrics metrics = new StockMetrics();
        metrics.setStockId(stockId);

        when(stockRepository.findById(stockId)).thenReturn(Optional.of(stock));
        when(stockMetricsRepository.findById(stockId)).thenReturn(Optional.of(metrics));
        when(stockMarketDataRepository.findRecentByStockId(eq(stockId), any(Pageable.class)))
                .thenReturn(List.of(latestDay, previousDay));
        when(allStocksLastValueCacheService.getCacheEntryByStockId(stockId)).thenReturn(null);

        StockInsightsResponseDTO response = stockInsightsService.getInsights(stockId);

        assertThat(response.volatilityMetrics().volatility90Day()).isEqualTo(22.22d);
        assertThat(response.momentumMetrics().rsi14()).isEqualTo(55.55d);
        assertThat(response.momentumMetrics().macd()).isEqualTo(1.23d);
        assertThat(response.momentumMetrics().macdSignal()).isEqualTo(0.98d);
    }

    private static Stock stock(Long id) {
        Stock stock = new Stock();
        stock.setStockId(id);
        stock.setSymbol("AAPL");
        stock.setName("Apple Inc.");
        stock.setMarket("stocks");
        return stock;
    }

    private static StockMarketData marketData(LocalDate tradingDate, String closePrice, Long volume) {
        StockMarketData data = new StockMarketData();
        data.setTradingDate(tradingDate);
        data.setOpenPrice(new BigDecimal(closePrice));
        data.setHighPrice(new BigDecimal(closePrice));
        data.setLowPrice(new BigDecimal(closePrice));
        data.setClosePrice(new BigDecimal(closePrice));
        data.setVolume(volume);
        return data;
    }
}


