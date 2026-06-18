package com.tradepulseai.orderservice.dto.order;

import java.math.BigDecimal;
import java.util.List;

public class CompleteOrderRequestDTO {

    private List<CompleteOrderItemRequestDTO> items;
    private BigDecimal subtotal;
    private BigDecimal tax;
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
}

