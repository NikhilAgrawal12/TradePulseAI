package com.tradepulseai.custservice.dto.watchlist;

import java.math.BigDecimal;

public class WatchlistItemResponseDTO {

    private String stockId;
    private BigDecimal quantity;

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}

