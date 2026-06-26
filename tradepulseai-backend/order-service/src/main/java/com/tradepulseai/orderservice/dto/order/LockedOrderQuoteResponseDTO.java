package com.tradepulseai.orderservice.dto.order;

import java.math.BigDecimal;
import java.util.List;

public class LockedOrderQuoteResponseDTO {

    private List<CompleteOrderItemRequestDTO> items;
    private BigDecimal subtotal;
    private BigDecimal total;
    private int lockSeconds;

    public List<CompleteOrderItemRequestDTO> getItems() {
        return items;
    }

    public void setItems(List<CompleteOrderItemRequestDTO> items) {
        this.items = items;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public int getLockSeconds() {
        return lockSeconds;
    }

    public void setLockSeconds(int lockSeconds) {
        this.lockSeconds = lockSeconds;
    }
}

