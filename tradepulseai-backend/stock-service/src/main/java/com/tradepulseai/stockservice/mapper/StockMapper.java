package com.tradepulseai.stockservice.mapper;

import com.tradepulseai.stockservice.dto.stock.StockResponseDTO;
import com.tradepulseai.stockservice.model.Stock;

public class StockMapper {

    private StockMapper() {
    }

    public static StockResponseDTO toDTO(Stock stock) {
        StockResponseDTO dto = new StockResponseDTO();

        dto.setId(String.valueOf(stock.getStockId()));
        dto.setSymbol(stock.getSymbol());
        dto.setName(stock.getName());
        dto.setExchange(stock.getExchange());
        dto.setMarket(stock.getMarket());
        dto.setLocale(stock.getLocale());
        dto.setActive(stock.getActive());
        dto.setPrice(stock.getPrice() == null ? null : stock.getPrice().doubleValue());
        dto.setChangePercent(stock.getChangePercent() == null ? null : stock.getChangePercent().doubleValue());
        dto.setVolume(stock.getVolume());
        dto.setLastUpdated(stock.getLastUpdated() == null ? null : stock.getLastUpdated().toString());
        dto.setSource(stock.getSource());

        return dto;
    }
}
