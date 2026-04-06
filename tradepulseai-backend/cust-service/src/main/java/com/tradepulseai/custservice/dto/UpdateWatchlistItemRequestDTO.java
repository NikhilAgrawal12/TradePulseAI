package com.tradepulseai.custservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class UpdateWatchlistItemRequestDTO {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal refPrice;

    @Min(1)
    private int quantity;

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

