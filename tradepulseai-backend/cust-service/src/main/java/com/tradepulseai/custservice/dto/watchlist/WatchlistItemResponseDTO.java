package com.tradepulseai.custservice.dto.watchlist;

public class WatchlistItemResponseDTO {

    private String stockId;
    private Long quantity;

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

}

