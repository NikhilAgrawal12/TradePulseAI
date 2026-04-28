package com.tradepulseai.stockservice.mapper;

import com.tradepulseai.stockservice.dto.stock.StockRatingDTO;
import com.tradepulseai.stockservice.dto.stock.StockResponseDTO;
import com.tradepulseai.stockservice.model.Stock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StockMapper {

    private StockMapper() {
    }

    public static StockResponseDTO toDTO(Stock stock) {
        StockResponseDTO dto = new StockResponseDTO();
        StockRatingDTO ratingDTO = new StockRatingDTO();

        ratingDTO.setScore(stock.getRatingScore().doubleValue());
        ratingDTO.setAnalysts(stock.getAnalystCount());

        dto.setId(String.valueOf(stock.getStockId()));
        dto.setSymbol(stock.getSymbol());
        dto.setName(stock.getName());
        dto.setSector(stock.getSector());
        dto.setExchange(stock.getExchange());
        dto.setPrice(stock.getPrice().doubleValue());
        dto.setChangePercent(stock.getChangePercent().doubleValue());
        dto.setRating(ratingDTO);
        dto.setMarketCapBillion(stock.getMarketCapBillion().doubleValue());
        dto.setVolume(stock.getVolume());
        dto.setRecommendation(stock.getRecommendation());
        dto.setKeywords(buildKeywords(stock));

        return dto;
    }

    private static List<String> buildKeywords(Stock stock) {
        List<String> keywords = new ArrayList<>();
        if (stock.getSector() != null && !stock.getSector().isBlank()) {
            keywords.add(stock.getSector().toLowerCase(Locale.ROOT).replace(' ', '-'));
        }
        if (stock.getExchange() != null && !stock.getExchange().isBlank()) {
            keywords.add(stock.getExchange().toLowerCase(Locale.ROOT));
        }
        if (stock.getRecommendation() != null) {
            keywords.add(stock.getRecommendation().name().toLowerCase(Locale.ROOT));
        }
        return keywords;
    }
}
