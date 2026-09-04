package com.tradepulse.orderservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

@Entity
@Table(name = "quote_lock_items", indexes = {
    @Index(name = "idx_quote_lock_items_quote_lock_id", columnList = "quote_lock_id")
})
public class QuoteLockItem {

    @EmbeddedId
    private QuoteLockItemId id;

    @MapsId("quoteLockId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_lock_id", nullable = false, columnDefinition = "VARCHAR(36)")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private QuoteLock quoteLock;

    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    @Column(name = "price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;

    public QuoteLockItemId getId() {
        return id;
    }

    public void setId(QuoteLockItemId id) {
        this.id = id;
    }

    public QuoteLock getQuoteLock() {
        return quoteLock;
    }

    public void setQuoteLock(QuoteLock quoteLock) {
        this.quoteLock = quoteLock;
    }

    public String getStockId() {
        return id != null ? id.getStockId() : null;
    }

    public void setStockId(String stockId) {
        if (this.id == null) {
            this.id = new QuoteLockItemId();
        }
        this.id.setStockId(stockId);
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
}

