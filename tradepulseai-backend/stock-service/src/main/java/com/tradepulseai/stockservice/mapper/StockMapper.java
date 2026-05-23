package com.tradepulseai.stockservice.mapper;

import com.tradepulseai.stockservice.dto.stock.StockResponseDTO;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class StockMapper {

    private StockMapper() {
    }

    public static StockResponseDTO toDTO(Stock stock, StockMarketData latestMarketData) {
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

        if (latestMarketData != null) {
            dto.setPrice(latestMarketData.getClosePrice() == null ? null : latestMarketData.getClosePrice().doubleValue());
            dto.setChangePercent(calculateChangePercent(latestMarketData.getOpenPrice(), latestMarketData.getClosePrice()));
            dto.setVolume(latestMarketData.getVolume());
            dto.setLastUpdated(latestMarketData.getUpdatedAt() == null ? null : latestMarketData.getUpdatedAt().toString());
        }

        return dto;
    }

    private static Double calculateChangePercent(BigDecimal openPrice, BigDecimal closePrice) {
        if (openPrice == null || closePrice == null || openPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return closePrice
                .subtract(openPrice)
                .divide(openPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
