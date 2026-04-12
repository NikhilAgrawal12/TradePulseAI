package com.tradepulseai.custservice.service;

import com.tradepulseai.custservice.client.AuthServiceClient;
import com.tradepulseai.custservice.client.StockCatalogClient;
import com.tradepulseai.custservice.dto.PortfolioFillItemRequestDTO;
import com.tradepulseai.custservice.dto.PortfolioHoldingResponseDTO;
import com.tradepulseai.custservice.dto.PortfolioResponseDTO;
import com.tradepulseai.custservice.dto.PortfolioSummaryResponseDTO;
import com.tradepulseai.custservice.dto.PortfolioTransactionResponseDTO;
import com.tradepulseai.custservice.dto.RecordPortfolioOrderRequestDTO;
import com.tradepulseai.custservice.dto.SellPortfolioItemRequestDTO;
import com.tradepulseai.custservice.mapper.PortfolioMapper;
import com.tradepulseai.custservice.model.PortfolioHolding;
import com.tradepulseai.custservice.model.PortfolioTransaction;
import com.tradepulseai.custservice.repository.PortfolioHoldingRepository;
import com.tradepulseai.custservice.repository.PortfolioTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioService {

    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final PortfolioTransactionRepository portfolioTransactionRepository;
    private final AuthServiceClient authServiceClient;
    private final StockCatalogClient stockCatalogClient;

    public PortfolioService(
            PortfolioHoldingRepository portfolioHoldingRepository,
            PortfolioTransactionRepository portfolioTransactionRepository,
            AuthServiceClient authServiceClient,
            StockCatalogClient stockCatalogClient
    ) {
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.portfolioTransactionRepository = portfolioTransactionRepository;
        this.authServiceClient = authServiceClient;
        this.stockCatalogClient = stockCatalogClient;
    }

    @Transactional(readOnly = true)
    public PortfolioResponseDTO getPortfolio(String userEmail) {
        Long userId = authServiceClient.getUserByEmail(userEmail).userId();
        List<PortfolioHolding> holdings = portfolioHoldingRepository.findByIdUserIdOrderByUpdatedAtDesc(userId);
        List<PortfolioTransaction> transactions = portfolioTransactionRepository.findByUserIdOrderByExecutedAtDesc(userId);

        Map<Long, StockCatalogClient.StockQuote> stockQuotes = loadStockQuotes(holdings, transactions);
        PortfolioAnalytics analytics = calculateAnalytics(transactions);

        List<PortfolioHoldingResponseDTO> holdingResponses = holdings.stream()
                .map(holding -> {
                    Long stockId = holding.getId().getStockId();
                    StockCatalogClient.StockQuote quote = resolveQuote(stockQuotes, stockId);
                    BigDecimal averageBuyPrice = analytics.averageBuyByStock().getOrDefault(stockId, BigDecimal.ZERO);
                    BigDecimal realizedPnl = analytics.realizedByStock().getOrDefault(stockId, BigDecimal.ZERO);
                    return PortfolioMapper.toHoldingResponse(holding, quote, averageBuyPrice, realizedPnl);
                })
                .toList();

        List<PortfolioTransactionResponseDTO> transactionResponses = transactions.stream()
                .map(transaction -> {
                    Long stockId = transaction.getStockId();
                    StockCatalogClient.StockQuote quote = resolveQuote(stockQuotes, stockId);
                    BigDecimal realizedPnl = analytics.realizedByTransactionId()
                            .getOrDefault(transaction.getTransactionId(), BigDecimal.ZERO);
                    return PortfolioMapper.toTransactionResponse(transaction, quote.symbol(), realizedPnl);
                })
                .toList();

        PortfolioResponseDTO response = new PortfolioResponseDTO();
        response.setSummary(toSummary(holdingResponses));
        response.setHoldings(holdingResponses);
        response.setTransactions(transactionResponses);
        return response;
    }

    @Transactional
    public PortfolioResponseDTO recordCompletedOrder(String userEmail, RecordPortfolioOrderRequestDTO request) {
        Long userId = authServiceClient.getUserByEmail(userEmail).userId();
        for (PortfolioFillItemRequestDTO item : request.getItems()) {
            Long stockId = parseStockId(item.getStockId());
            PortfolioHolding holding = portfolioHoldingRepository.findByIdUserIdAndIdStockId(userId, stockId)
                    .orElseGet(() -> PortfolioMapper.newHolding(userId, item));

            if (holding.getId() != null && holding.getTotalQuantity() != null) {
                mergeBuyIntoHolding(holding, item);
            }

            portfolioHoldingRepository.save(holding);
            portfolioTransactionRepository.save(PortfolioMapper.toBuyTransaction(userId, item));
        }

        return getPortfolio(userEmail);
    }

    @Transactional
    public PortfolioResponseDTO sellPosition(String userEmail, String stockId, SellPortfolioItemRequestDTO request) {
        Long userId = authServiceClient.getUserByEmail(userEmail).userId();
        Long parsedStockId = parseStockId(stockId);

        PortfolioHolding holding = portfolioHoldingRepository.findByIdUserIdAndIdStockId(userId, parsedStockId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio holding not found for stockId: " + stockId));

        BigDecimal requestedQty = PortfolioMapper.scaleQuantity(BigDecimal.valueOf(request.getQuantity()));
        if (requestedQty.compareTo(holding.getTotalQuantity()) > 0) {
            throw new IllegalArgumentException("Sell quantity cannot be greater than owned quantity.");
        }

        portfolioTransactionRepository.save(
                PortfolioMapper.toSellTransaction(
                        userId,
                        holding.getId().getStockId(),
                        request.getQuantity(),
                        request.getPrice()
                )
        );

        BigDecimal remainingQuantity = holding.getTotalQuantity().subtract(requestedQty);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            portfolioHoldingRepository.delete(holding);
        } else {
            holding.setTotalQuantity(PortfolioMapper.scaleQuantity(remainingQuantity));
            portfolioHoldingRepository.save(holding);
        }

        return getPortfolio(userEmail);
    }

    private void mergeBuyIntoHolding(PortfolioHolding holding, PortfolioFillItemRequestDTO item) {
        BigDecimal updatedQuantity = holding.getTotalQuantity().add(BigDecimal.valueOf(item.getQuantity()));
        holding.setTotalQuantity(PortfolioMapper.scaleQuantity(updatedQuantity));
    }

    private Long parseStockId(String stockId) {
        try {
            return Long.parseLong(stockId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid stockId format: " + stockId);
        }
    }

    private Map<Long, StockCatalogClient.StockQuote> loadStockQuotes(
            List<PortfolioHolding> holdings,
            List<PortfolioTransaction> transactions
    ) {
        Map<Long, StockCatalogClient.StockQuote> quotes = new LinkedHashMap<>();
        for (PortfolioHolding holding : holdings) {
            quotes.computeIfAbsent(holding.getId().getStockId(), stockCatalogClient::getStockQuote);
        }
        for (PortfolioTransaction transaction : transactions) {
            quotes.computeIfAbsent(transaction.getStockId(), stockCatalogClient::getStockQuote);
        }
        return quotes;
    }

    private StockCatalogClient.StockQuote resolveQuote(Map<Long, StockCatalogClient.StockQuote> stockQuotes, Long stockId) {
        StockCatalogClient.StockQuote quote = stockQuotes.get(stockId);
        if (quote == null) {
            throw new IllegalArgumentException("Missing stock quote for stockId: " + stockId);
        }
        return quote;
    }

    private PortfolioAnalytics calculateAnalytics(List<PortfolioTransaction> transactions) {
        Map<Long, CostBasisState> stateByStock = new LinkedHashMap<>();
        Map<Long, BigDecimal> realizedByTransactionId = new LinkedHashMap<>();

        transactions.stream()
                .sorted(Comparator.comparing(PortfolioTransaction::getExecutedAt))
                .forEach(transaction -> {
                    Long stockId = transaction.getStockId();
                    CostBasisState state = stateByStock.computeIfAbsent(stockId, ignored -> new CostBasisState());
                    BigDecimal quantity = PortfolioMapper.scaleQuantity(transaction.getQuantity());
                    BigDecimal price = PortfolioMapper.scaleMoney(transaction.getPrice());

                    if ("BUY".equalsIgnoreCase(transaction.getTransactionType())) {
                        state.totalCost = state.totalCost.add(price.multiply(quantity));
                        state.quantity = state.quantity.add(quantity);
                        realizedByTransactionId.put(transaction.getTransactionId(), BigDecimal.ZERO);
                        return;
                    }

                    BigDecimal averageCost = state.averageCost();
                    BigDecimal realized = price.subtract(averageCost).multiply(quantity);
                    state.realizedPnl = state.realizedPnl.add(realized);
                    state.quantity = state.quantity.subtract(quantity);
                    state.totalCost = state.totalCost.subtract(averageCost.multiply(quantity));
                    if (state.quantity.compareTo(BigDecimal.ZERO) < 0) {
                        state.quantity = BigDecimal.ZERO;
                        state.totalCost = BigDecimal.ZERO;
                    }
                    realizedByTransactionId.put(transaction.getTransactionId(), PortfolioMapper.scaleMoney(realized));
                });

        Map<Long, BigDecimal> averageBuyByStock = new LinkedHashMap<>();
        Map<Long, BigDecimal> realizedByStock = new LinkedHashMap<>();
        for (Map.Entry<Long, CostBasisState> entry : stateByStock.entrySet()) {
            Long stockId = entry.getKey();
            CostBasisState state = entry.getValue();
            averageBuyByStock.put(stockId, state.averageCost());
            realizedByStock.put(stockId, PortfolioMapper.scaleMoney(state.realizedPnl));
        }

        return new PortfolioAnalytics(averageBuyByStock, realizedByStock, realizedByTransactionId);
    }

    private static class CostBasisState {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal totalCost = BigDecimal.ZERO;
        private BigDecimal realizedPnl = BigDecimal.ZERO;

        private BigDecimal averageCost() {
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            }
            return totalCost.divide(quantity, 4, RoundingMode.HALF_UP);
        }
    }

    private record PortfolioAnalytics(
            Map<Long, BigDecimal> averageBuyByStock,
            Map<Long, BigDecimal> realizedByStock,
            Map<Long, BigDecimal> realizedByTransactionId
    ) {
    }

    private PortfolioSummaryResponseDTO toSummary(List<PortfolioHoldingResponseDTO> holdings) {
        BigDecimal totalInvested = holdings.stream()
                .map(PortfolioHoldingResponseDTO::getInvestedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMarketValue = holdings.stream()
                .map(PortfolioHoldingResponseDTO::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnrealized = holdings.stream()
                .map(PortfolioHoldingResponseDTO::getUnrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRealized = holdings.stream()
                .map(PortfolioHoldingResponseDTO::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalQuantity = holdings.stream()
                .mapToInt(PortfolioHoldingResponseDTO::getQuantity)
                .sum();

        BigDecimal totalUnrealizedPercent = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            totalUnrealizedPercent = totalUnrealized
                    .divide(totalInvested, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        PortfolioSummaryResponseDTO summary = new PortfolioSummaryResponseDTO();
        summary.setTotalPositions(holdings.size());
        summary.setTotalQuantity(totalQuantity);
        summary.setTotalInvestedValue(PortfolioMapper.scaleMoney(totalInvested));
        summary.setTotalMarketValue(PortfolioMapper.scaleMoney(totalMarketValue));
        summary.setTotalUnrealizedPnl(PortfolioMapper.scaleMoney(totalUnrealized));
        summary.setTotalUnrealizedPnlPercent(totalUnrealizedPercent.setScale(2, RoundingMode.HALF_UP));
        summary.setTotalRealizedPnl(PortfolioMapper.scaleMoney(totalRealized));
        return summary;
    }
}
