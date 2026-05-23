package com.tradepulseai.orderservice.dto.order;

public class CompleteOrderResponseDTO {

    private String orderId;
    private String accountId;
    private String status;

    public CompleteOrderResponseDTO() {
    }

    public CompleteOrderResponseDTO(String orderId, String accountId, String status) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.status = status;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
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
