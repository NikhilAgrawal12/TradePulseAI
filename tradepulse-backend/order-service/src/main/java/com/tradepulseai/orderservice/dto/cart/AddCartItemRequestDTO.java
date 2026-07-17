package com.tradepulseai.orderservice.dto.cart;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class AddCartItemRequestDTO {

    @NotBlank
    private String stockId;

    @NotNull
    @DecimalMin(value = "0.0001")
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

