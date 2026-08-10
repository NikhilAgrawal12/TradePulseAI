package com.tradepulse.portfolioservice.dto;

import java.util.List;

public class PortfolioResponseDTO {
    private PortfolioSummaryResponseDTO summary;
    private List<PortfolioHoldingResponseDTO> holdings;
    private List<PortfolioTransactionResponseDTO> transactions;
    private int transactionPage;
    private int transactionPageSize;
    private long transactionTotalElements;
    private int transactionTotalPages;
    private boolean transactionFirst;
    private boolean transactionLast;

    public PortfolioSummaryResponseDTO getSummary() { return summary; }
    public void setSummary(PortfolioSummaryResponseDTO summary) { this.summary = summary; }
    public List<PortfolioHoldingResponseDTO> getHoldings() { return holdings; }
    public void setHoldings(List<PortfolioHoldingResponseDTO> holdings) { this.holdings = holdings; }
    public List<PortfolioTransactionResponseDTO> getTransactions() { return transactions; }
    public void setTransactions(List<PortfolioTransactionResponseDTO> transactions) { this.transactions = transactions; }
    public int getTransactionPage() { return transactionPage; }
    public void setTransactionPage(int transactionPage) { this.transactionPage = transactionPage; }
    public int getTransactionPageSize() { return transactionPageSize; }
    public void setTransactionPageSize(int transactionPageSize) { this.transactionPageSize = transactionPageSize; }
    public long getTransactionTotalElements() { return transactionTotalElements; }
    public void setTransactionTotalElements(long transactionTotalElements) { this.transactionTotalElements = transactionTotalElements; }
    public int getTransactionTotalPages() { return transactionTotalPages; }
    public void setTransactionTotalPages(int transactionTotalPages) { this.transactionTotalPages = transactionTotalPages; }
    public boolean isTransactionFirst() { return transactionFirst; }
    public void setTransactionFirst(boolean transactionFirst) { this.transactionFirst = transactionFirst; }
    public boolean isTransactionLast() { return transactionLast; }
    public void setTransactionLast(boolean transactionLast) { this.transactionLast = transactionLast; }
}

