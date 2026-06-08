package com.tradepulseai.paymentservice.dto.payment;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentResponseDTO {

    private Long id;
    private String orderId;
    private BigDecimal totalAmount;
    private String status;
    private Instant createdAt;

    public PaymentResponseDTO() {
    }

    public PaymentResponseDTO(Long id, String orderId, BigDecimal totalAmount, String status, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

