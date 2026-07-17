package com.tradepulse.portfolioservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "portfolio_holdings", indexes = {
    @Index(name = "idx_portfolio_holdings_user_id", columnList = "user_id")
})
public class PortfolioHolding {

    @EmbeddedId
    private PortfolioHoldingId id;

    @Column(name = "total_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalQuantity;

    @Column(name = "avg_buy_price", precision = 18, scale = 2)
    private BigDecimal avgBuyPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.totalQuantity == null) {
            this.totalQuantity = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public PortfolioHoldingId getId() { return id; }
    public void setId(PortfolioHoldingId id) { this.id = id; }

    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }

    public BigDecimal getAvgBuyPrice() { return avgBuyPrice; }
    public void setAvgBuyPrice(BigDecimal avgBuyPrice) { this.avgBuyPrice = avgBuyPrice; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

