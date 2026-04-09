package com.tradepulseai.custservice.dto;

import java.util.List;

public class PortfolioResponseDTO {

    private PortfolioSummaryResponseDTO summary;
    private List<PortfolioHoldingResponseDTO> holdings;
    private List<PortfolioTransactionResponseDTO> transactions;

    public PortfolioSummaryResponseDTO getSummary() {
        return summary;
    }

    public void setSummary(PortfolioSummaryResponseDTO summary) {
        this.summary = summary;
    }

    public List<PortfolioHoldingResponseDTO> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<PortfolioHoldingResponseDTO> holdings) {
        this.holdings = holdings;
    }

    public List<PortfolioTransactionResponseDTO> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<PortfolioTransactionResponseDTO> transactions) {
        this.transactions = transactions;
    }
}

