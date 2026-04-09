package com.tradepulseai.orderservice.dto;

public class CompleteOrderResponseDTO {

    private String accountId;
    private String status;

    public CompleteOrderResponseDTO() {
    }

    public CompleteOrderResponseDTO(String accountId, String status) {
        this.accountId = accountId;
        this.status = status;
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

