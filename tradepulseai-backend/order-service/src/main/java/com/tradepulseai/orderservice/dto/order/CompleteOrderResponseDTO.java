package com.tradepulseai.orderservice.dto.order;

public class CompleteOrderResponseDTO {

    private Long orderId;
    private String accountId;
    private String status;

    public CompleteOrderResponseDTO() {
    }

    public CompleteOrderResponseDTO(Long orderId, String accountId, String status) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.status = status;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
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

