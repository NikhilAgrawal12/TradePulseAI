package com.tradepulseai.paymentservice.dto.wallet;

import java.math.BigDecimal;
import java.time.Instant;

public class WalletResponseDTO {

    private Long walletId;
    private Long userId;
    private BigDecimal balance;
    private Instant createdAt;
    private Instant updatedAt;

    public WalletResponseDTO() {}

    public WalletResponseDTO(Long walletId, Long userId, BigDecimal balance,
                              Instant createdAt, Instant updatedAt) {
        this.walletId = walletId;
        this.userId = userId;
        this.balance = balance;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getWalletId() { return walletId; }
    public void setWalletId(Long walletId) { this.walletId = walletId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

