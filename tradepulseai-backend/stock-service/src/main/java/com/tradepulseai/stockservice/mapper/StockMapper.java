package com.tradepulseai.stockservice.mapper;

import com.tradepulseai.stockservice.dto.stock.StockResponseDTO;
import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;

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
            dto.setChangePercent(latestMarketData.getChangePercent() == null ? null : latestMarketData.getChangePercent().doubleValue());
            dto.setVolume(latestMarketData.getVolume());
            dto.setLastUpdated(latestMarketData.getMarketTimestamp() == null ? null : latestMarketData.getMarketTimestamp().toString());
            dto.setSource(latestMarketData.getSource());
        }

        return dto;
    }
}
