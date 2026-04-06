package com.tradepulseai.orderservice.dto;

import jakarta.validation.constraints.Min;

public class UpdateCartItemRequestDTO {

    @Min(1)
    private int quantity;

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

