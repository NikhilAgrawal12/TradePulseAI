package com.tradepulse.orderservice.dto.order;

import java.math.BigDecimal;
import java.util.List;

public class CompleteOrderRequestDTO {

    private String quoteLockId;
    private List<CompleteOrderItemRequestDTO> items;
    private BigDecimal total;

    public String getQuoteLockId() {
        return quoteLockId;
    }

    public void setQuoteLockId(String quoteLockId) {
        this.quoteLockId = quoteLockId;
    }

    public List<CompleteOrderItemRequestDTO> getItems() {
        return items;
    }

    public void setItems(List<CompleteOrderItemRequestDTO> items) {
        this.items = items;
    }


    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}
