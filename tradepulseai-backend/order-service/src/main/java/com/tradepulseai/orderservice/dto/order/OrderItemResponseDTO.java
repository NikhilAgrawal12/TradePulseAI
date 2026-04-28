package com.tradepulseai.orderservice.dto.order;

import java.math.BigDecimal;

public class OrderItemResponseDTO {

    private String stockId;
    private String symbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal lineTotal;

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

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }
}

