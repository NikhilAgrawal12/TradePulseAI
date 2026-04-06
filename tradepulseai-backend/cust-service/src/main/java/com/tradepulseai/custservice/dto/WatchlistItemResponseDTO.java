package com.tradepulseai.custservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class WatchlistItemResponseDTO {

    private UUID id;
    private String stockId;
    private String symbol;
    private BigDecimal refPrice;
    private int quantity;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getRefPrice() {
        return refPrice;
    }

    public void setRefPrice(BigDecimal refPrice) {
        this.refPrice = refPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

