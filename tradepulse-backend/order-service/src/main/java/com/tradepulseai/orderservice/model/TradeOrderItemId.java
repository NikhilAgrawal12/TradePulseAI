package com.tradepulseai.orderservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TradeOrderItemId implements Serializable {

    @Column(name = "order_id", length = 36, nullable = false)
    private String orderId;

    @Column(name = "stock_id", length = 36, nullable = false)
    private String stockId;

    public TradeOrderItemId() {
    }

    public TradeOrderItemId(String orderId, String stockId) {
        this.orderId = orderId;
        this.stockId = stockId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
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
