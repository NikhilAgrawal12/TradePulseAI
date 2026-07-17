package com.tradepulseai.stockservice.mapper;

import com.tradepulseai.stockservice.dto.stock.StockResponseDTO;
import com.tradepulseai.stockservice.model.AllStocksLastValueCache;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class StockMapper {

    private StockMapper() {
    }

    public static StockResponseDTO toDTO(Stock stock, StockMarketData latestMarketData) {
        StockResponseDTO dto = baseDTO(stock);

        if (latestMarketData != null) {
            dto.setOpen(toTwoDecimals(latestMarketData.getOpenPrice()));
            dto.setHigh(toTwoDecimals(latestMarketData.getHighPrice()));
            dto.setLow(toTwoDecimals(latestMarketData.getLowPrice()));
            dto.setPrice(toTwoDecimals(latestMarketData.getClosePrice()));
            dto.setVwap(toTwoDecimals(latestMarketData.getVwap()));
            dto.setChangePercent(calculateChangePercent(latestMarketData.getOpenPrice(), latestMarketData.getClosePrice()));
            dto.setVolume(latestMarketData.getVolume());
            dto.setLastUpdated(latestMarketData.getUpdatedAt() == null ? null : latestMarketData.getUpdatedAt().toString());
        }

        return dto;
    }

    public static StockResponseDTO toDTO(AllStocksLastValueCache cacheEntry) {
        StockResponseDTO dto = baseDTO(cacheEntry.getStock());
        dto.setOpen(toTwoDecimals(cacheEntry.getCachedOpen()));
        dto.setHigh(toTwoDecimals(cacheEntry.getCachedHigh()));
        dto.setLow(toTwoDecimals(cacheEntry.getCachedLow()));
        dto.setPrice(toTwoDecimals(cacheEntry.getCachedClose()));
        dto.setVwap(toTwoDecimals(cacheEntry.getCachedVwap()));
        dto.setChangePercent(toTwoDecimals(cacheEntry.getCachedChangePercent()));
        dto.setVolume(cacheEntry.getCachedVolume());
        dto.setLastUpdated(cacheEntry.getAggregateUpdatedAt() == null ? null : cacheEntry.getAggregateUpdatedAt().toString());
        dto.setSource("all-stocks-cache");
        return dto;
    }

    private static StockResponseDTO baseDTO(Stock stock) {
        StockResponseDTO dto = new StockResponseDTO();
        dto.setId(String.valueOf(stock.getStockId()));
        dto.setSymbol(stock.getSymbol());
        dto.setName(stock.getName());
        dto.setExchange(stock.getExchange() == null
                ? null
                : (stock.getExchange().getAcronym() != null && !stock.getExchange().getAcronym().isBlank()
                    ? stock.getExchange().getAcronym()
                    : stock.getExchange().getMic()));
        dto.setMarket(stock.getMarket());
        dto.setLocale(stock.getLocale());
        dto.setActive(stock.getActive());
        return dto;
    }

    public static StockResponseDTO toDTOFromCache(Stock stock) {
        return baseDTO(stock);
    }

    private static Double calculateChangePercent(BigDecimal openPrice, BigDecimal closePrice) {
        if (openPrice == null || closePrice == null || openPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return closePrice
                .subtract(openPrice)
                .divide(openPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static Double toTwoDecimals(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
