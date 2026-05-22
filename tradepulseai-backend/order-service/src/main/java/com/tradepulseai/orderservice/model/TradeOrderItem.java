package com.tradepulseai.orderservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class TradeOrderItem {

    @EmbeddedId
    private TradeOrderItemId id;

    @MapsId("orderId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TradeOrder order;

    @Column(name = "price", nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    public TradeOrderItemId getId() {
        return id;
    }

    public void setId(TradeOrderItemId id) {
        this.id = id;
    }

    public TradeOrder getOrder() {
        return order;
    }

    public void setOrder(TradeOrder order) {
        this.order = order;
    }

    public Long getStockId() {
        return id != null ? id.getStockId() : null;
    }

    public void setStockId(Long stockId) {
        if (this.id == null) {
            this.id = new TradeOrderItemId();
        }
        this.id.setStockId(stockId);
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}

