package com.tradepulseai.orderservice.dto;

import java.util.UUID;

public class CompleteOrderResponseDTO {

    private UUID orderId;
    private String accountId;
    private String status;

    public CompleteOrderResponseDTO() {
    }

    public CompleteOrderResponseDTO(UUID orderId, String accountId, String status) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.status = status;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
