package com.tradepulseai.custservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class AddWatchlistItemRequestDTO {

    @NotBlank
    private String stockId;

    @NotBlank
    private String symbol;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal refPrice;

    @Min(1)
    private int quantity;

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

