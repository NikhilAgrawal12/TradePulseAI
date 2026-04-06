package com.tradepulseai.stockservice.mapper;

import com.tradepulseai.stockservice.dto.StockRatingDTO;
import com.tradepulseai.stockservice.dto.StockResponseDTO;
import com.tradepulseai.stockservice.model.Stock;

import java.util.ArrayList;

public class StockMapper {

    private StockMapper() {
    }

    public static StockResponseDTO toDTO(Stock stock) {
        StockResponseDTO dto = new StockResponseDTO();
        StockRatingDTO ratingDTO = new StockRatingDTO();

        ratingDTO.setScore(stock.getRatingScore());
        ratingDTO.setAnalysts(stock.getAnalystCount());

        dto.setId(stock.getId());
        dto.setSymbol(stock.getSymbol());
        dto.setName(stock.getName());
        dto.setSector(stock.getSector());
        dto.setExchange(stock.getExchange());
        dto.setPrice(stock.getPrice());
        dto.setChangePercent(stock.getChangePercent());
        dto.setRating(ratingDTO);
        dto.setMarketCapBillion(stock.getMarketCapBillion());
        dto.setVolume(stock.getVolume());
        dto.setRecommendation(stock.getRecommendation());
        dto.setKeywords(new ArrayList<>(stock.getKeywords()));

        return dto;
    }
}

