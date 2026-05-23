package com.tradepulseai.custservice.dto.watchlist;

import jakarta.validation.constraints.NotBlank;

public class AddWatchlistItemRequestDTO {

    @NotBlank
    private String stockId;

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }

}

