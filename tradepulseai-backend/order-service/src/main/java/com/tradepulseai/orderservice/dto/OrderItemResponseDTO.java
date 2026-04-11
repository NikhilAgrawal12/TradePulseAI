package com.tradepulseai.orderservice.dto;

import java.math.BigDecimal;

public class OrderItemResponseDTO {

    private String stockId;
    private BigDecimal price;
    private BigDecimal quantity;

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setQuantityValue(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = BigDecimal.valueOf(quantity).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}

