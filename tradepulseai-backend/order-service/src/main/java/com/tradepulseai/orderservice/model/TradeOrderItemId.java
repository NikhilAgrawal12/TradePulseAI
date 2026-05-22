package com.tradepulseai.orderservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TradeOrderItemId implements Serializable {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    public TradeOrderItemId() {
    }

    public TradeOrderItemId(Long orderId, Long stockId) {
        this.orderId = orderId;
        this.stockId = stockId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TradeOrderItemId that)) {
            return false;
        }
        return Objects.equals(orderId, that.orderId) && Objects.equals(stockId, that.stockId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, stockId);
    }
}

