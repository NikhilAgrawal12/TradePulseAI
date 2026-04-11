package com.tradepulseai.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderResponseDTO {

    private Long id;
    private Long userId;
    private String status;
    private Instant createdAtIso;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private List<OrderItemResponseDTO> items;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAtIso() {
        return createdAtIso;
    }

    public void setCreatedAtIso(Instant createdAtIso) {
        this.createdAtIso = createdAtIso;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public List<OrderItemResponseDTO> getItems() {
        return items;
    }

    public void setItems(List<OrderItemResponseDTO> items) {
        this.items = items;
    }
}

