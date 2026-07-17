package com.tradepulse.orderservice.dto.order;

import java.math.BigDecimal;
import java.util.List;

public class CompleteOrderRequestDTO {

    private List<CompleteOrderItemRequestDTO> items;
    private BigDecimal subtotal;
    private BigDecimal total;

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
}
