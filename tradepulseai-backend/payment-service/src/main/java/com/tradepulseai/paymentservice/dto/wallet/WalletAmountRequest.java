package com.tradepulseai.paymentservice.dto.wallet;

import java.math.BigDecimal;

public class WalletAmountRequest {
    private BigDecimal amount;

    public WalletAmountRequest() {}

    public WalletAmountRequest(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

