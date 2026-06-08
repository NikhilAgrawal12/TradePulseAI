package com.tradepulseai.custservice.dto.watchlist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class AddWatchlistItemRequestDTO {

    @NotBlank
    private String stockId;

    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

}

