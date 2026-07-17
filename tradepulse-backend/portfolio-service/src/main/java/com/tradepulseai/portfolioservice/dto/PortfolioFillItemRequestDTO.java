package com.tradepulseai.portfolioservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class PortfolioFillItemRequestDTO {

    @NotBlank(message = "stockId is required")
    private String stockId;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.0001", message = "price must be greater than 0")
    private BigDecimal price;

    @Min(value = 1, message = "quantity must be at least 1")
    private int quantity;

    public String getStockId() { return stockId; }
    public void setStockId(String stockId) { this.stockId = stockId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}

