package com.tradepulse.paymentservice.dto.wallet;

import java.math.BigDecimal;
import java.time.Instant;

public class WalletTransactionDTO {

    private String transactionId;
    private Long walletId;
    private String transactionType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Instant createdAt;

    public WalletTransactionDTO() {}

    public WalletTransactionDTO(String transactionId, Long walletId, String transactionType,
                                 BigDecimal amount, BigDecimal balanceAfter, Instant createdAt) {
        this.transactionId = transactionId;
        this.walletId = walletId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public Long getWalletId() { return walletId; }
    public void setWalletId(Long walletId) { this.walletId = walletId; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

