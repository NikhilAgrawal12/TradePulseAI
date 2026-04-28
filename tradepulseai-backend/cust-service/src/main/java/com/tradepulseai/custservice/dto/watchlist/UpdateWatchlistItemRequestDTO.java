package com.tradepulseai.custservice.dto.watchlist;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class UpdateWatchlistItemRequestDTO {

    @NotNull
    @DecimalMin(value = "0.0001", inclusive = true)
    private BigDecimal quantity;

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}

