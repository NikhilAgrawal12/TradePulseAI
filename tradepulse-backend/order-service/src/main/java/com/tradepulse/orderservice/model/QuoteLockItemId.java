package com.tradepulse.orderservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class QuoteLockItemId implements Serializable {

    @Column(name = "quote_lock_id", length = 36, nullable = false)
    private String quoteLockId;

    @Column(name = "stock_id", nullable = false)
    private String stockId;

    public String getQuoteLockId() {
        return quoteLockId;
    }

    public void setQuoteLockId(String quoteLockId) {
        this.quoteLockId = quoteLockId;
    }

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuoteLockItemId that)) return false;
        return Objects.equals(quoteLockId, that.quoteLockId) && Objects.equals(stockId, that.stockId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quoteLockId, stockId);
    }
}

