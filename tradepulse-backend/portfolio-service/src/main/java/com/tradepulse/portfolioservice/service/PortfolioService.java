package com.tradepulse.portfolioservice.service;

import com.tradepulse.portfolioservice.client.StockCatalogClient;
import com.tradepulse.portfolioservice.client.CustomerClient;
import com.tradepulse.portfolioservice.dto.PortfolioFillItemRequestDTO;
import com.tradepulse.portfolioservice.dto.PortfolioHoldingResponseDTO;
import com.tradepulse.portfolioservice.dto.PortfolioResponseDTO;
import com.tradepulse.portfolioservice.dto.PortfolioSummaryResponseDTO;
import com.tradepulse.portfolioservice.dto.PortfolioTransactionResponseDTO;
import com.tradepulse.portfolioservice.dto.RecordPortfolioOrderRequestDTO;
import com.tradepulse.portfolioservice.dto.SellPortfolioItemRequestDTO;
import com.tradepulse.portfolioservice.grpc.OrderPaymentGrpcClient;
import com.tradepulse.portfolioservice.kafka.NotificationKafkaProducer;
import com.tradepulse.portfolioservice.mapper.PortfolioMapper;
import com.tradepulse.portfolioservice.model.PortfolioHolding;
import com.tradepulse.portfolioservice.model.PortfolioTransaction;
import com.tradepulse.portfolioservice.repository.PortfolioHoldingRepository;
import com.tradepulse.portfolioservice.repository.PortfolioTransactionRepository;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PortfolioService {

    private static final long PORTFOLIO_CACHE_TTL_MS = 30_000;
    private static final int PORTFOLIO_CACHE_MAX_ENTRIES = 2_000;

    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final PortfolioTransactionRepository portfolioTransactionRepository;
    private final StockCatalogClient stockCatalogClient;
    private final OrderPaymentGrpcClient orderPaymentGrpcClient;
    private final NotificationKafkaProducer notificationKafkaProducer;
    private final CustomerClient customerClient;
    private final ConcurrentMap<Long, CachedValue<PortfolioResponseDTO>> portfolioCache = new ConcurrentHashMap<>();

    public PortfolioService(
            PortfolioHoldingRepository portfolioHoldingRepository,
            PortfolioTransactionRepository portfolioTransactionRepository,
            StockCatalogClient stockCatalogClient,
            OrderPaymentGrpcClient orderPaymentGrpcClient,
            NotificationKafkaProducer notificationKafkaProducer,
            CustomerClient customerClient
    ) {
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.portfolioTransactionRepository = portfolioTransactionRepository;
        this.stockCatalogClient = stockCatalogClient;
        this.orderPaymentGrpcClient = orderPaymentGrpcClient;
        this.notificationKafkaProducer = notificationKafkaProducer;
        this.customerClient = customerClient;
    }

    @Transactional(readOnly = true)
    public PortfolioResponseDTO getPortfolio(Long userId) {
        PortfolioResponseDTO cached = getFromCache(userId);
        if (cached != null) {
            return cached;
        }

        List<PortfolioHolding> holdings = portfolioHoldingRepository.findByIdUserIdOrderByUpdatedAtDesc(userId);
        List<PortfolioTransaction> transactions = portfolioTransactionRepository.findByUserIdOrderByExecutedAtDesc(userId);

        PortfolioAnalytics analytics = calculateAnalytics(transactions);

        List<PortfolioHoldingResponseDTO> holdingResponses = holdings.stream()
                .map(holding -> {
                    Long stockId = holding.getId().getStockId();
                    BigDecimal averageBuyPrice = analytics.averageBuyByStock().getOrDefault(stockId, BigDecimal.ZERO);
                    BigDecimal realizedPnl = analytics.realizedByStock().getOrDefault(stockId, BigDecimal.ZERO);
                    return PortfolioMapper.toHoldingResponse(holding, averageBuyPrice, realizedPnl);
                })
                .toList();

        List<PortfolioTransactionResponseDTO> transactionResponses = transactions.stream()
                .map(transaction -> {
                    BigDecimal realizedPnl = analytics.realizedByTransactionId()
                            .getOrDefault(transaction.getTransactionId(), BigDecimal.ZERO);
                    return PortfolioMapper.toTransactionResponse(transaction, realizedPnl);
                })
                .toList();

        PortfolioResponseDTO response = new PortfolioResponseDTO();
        response.setSummary(toSummary(holdingResponses, analytics));
        response.setHoldings(holdingResponses);
        response.setTransactions(transactionResponses);
        putIntoCache(userId, response);
        return response;
    }

    @Transactional
    public PortfolioResponseDTO recordCompletedOrder(Long userId, RecordPortfolioOrderRequestDTO request) {
        validateRecordCompletedOrderRequest(userId, request);

        for (PortfolioFillItemRequestDTO item : request.getItems()) {
            Long stockId = parseStockId(item.getStockId());
            PortfolioHolding holding = portfolioHoldingRepository
                    .findByIdUserIdAndIdStockId(userId, stockId).orElse(null);

            if (holding == null) {
                holding = PortfolioMapper.newHolding(userId, item);
            } else {
                BigDecimal updatedQuantity = holding.getTotalQuantity().add(BigDecimal.valueOf(item.getQuantity()));
                holding.setTotalQuantity(PortfolioMapper.scaleQuantity(updatedQuantity));
            }

            portfolioHoldingRepository.save(holding);
            portfolioTransactionRepository.save(PortfolioMapper.toBuyTransaction(userId, item));
        }

        evictPortfolioCache(userId);
        return getPortfolio(userId);
    }

    @Transactional
    public PortfolioResponseDTO sellPosition(Long userId, String stockId, SellPortfolioItemRequestDTO request) {
        StockCatalogClient.MarketSession marketSession = stockCatalogClient.getMarketSession();
        if (marketSession.stale() || !marketSession.canSell()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Markets are currently closed. Sell operations are only available during live market sessions."
            );
        }

        Long parsedStockId = parseStockId(stockId);
        PortfolioHolding holding = portfolioHoldingRepository.findByIdUserIdAndIdStockId(userId, parsedStockId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio holding not found for stockId: " + stockId));

        BigDecimal requestedQty = PortfolioMapper.scaleQuantity(BigDecimal.valueOf(request.getQuantity()));
        if (requestedQty.compareTo(holding.getTotalQuantity()) > 0) {
            throw new IllegalArgumentException("Sell quantity cannot be greater than owned quantity.");
        }

        PortfolioTransaction savedSellTransaction = portfolioTransactionRepository.save(
                PortfolioMapper.toSellTransaction(userId, holding.getId().getStockId(),
                        request.getQuantity(), request.getPrice())
        );

        BigDecimal settlementAmount = PortfolioMapper.scaleMoney(
                request.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()))
        );
        String settlementRef = "sell-" + savedSellTransaction.getTransactionId();
        try {
            var settlementResponse = orderPaymentGrpcClient.settleSell(
                    settlementRef,
                    userId,
                    holding.getId().getStockId(),
                    request.getQuantity(),
                    PortfolioMapper.scaleMoney(request.getPrice()),
                    settlementAmount
            );
            if (!"SELL_SETTLED".equalsIgnoreCase(settlementResponse.getStatus())) {
                throw new IllegalStateException("Sell settlement returned unexpected status: "
                        + settlementResponse.getStatus());
            }
        } catch (StatusRuntimeException settlementException) {
            throw new IllegalStateException(
                    "Sell settlement failed for stockId: " + stockId,
                    settlementException
            );
        }

        BigDecimal remainingQuantity = holding.getTotalQuantity().subtract(requestedQty);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            portfolioHoldingRepository.delete(holding);
        } else {
            holding.setTotalQuantity(PortfolioMapper.scaleQuantity(remainingQuantity));
            portfolioHoldingRepository.save(holding);
        }

        // Fetch customer data for email personalization.
        CustomerClient.CustomerInfo customerInfo = customerClient.getCustomer(userId);
        publishAfterCommit(() -> notificationKafkaProducer.publishStockSold(
                userId,
                customerInfo.firstName(),
                customerInfo.lastName(),
                holding.getId().getStockId(),
                request.getQuantity(),
                PortfolioMapper.scaleMoney(request.getPrice()),
                settlementAmount
        ));

        evictPortfolioCache(userId);
        return getPortfolio(userId);
    }

    private PortfolioResponseDTO getFromCache(Long userId) {
        CachedValue<PortfolioResponseDTO> cached = portfolioCache.get(userId);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAtEpochMs() < System.currentTimeMillis()) {
            portfolioCache.remove(userId, cached);
            return null;
        }
        return cached.value();
    }

    private void putIntoCache(Long userId, PortfolioResponseDTO value) {
        evictExpiredEntries();
        if (portfolioCache.size() >= PORTFOLIO_CACHE_MAX_ENTRIES) {
            evictOldestEntry();
        }
        portfolioCache.put(userId, new CachedValue<>(value, System.currentTimeMillis() + PORTFOLIO_CACHE_TTL_MS));
    }

    private void evictPortfolioCache(Long userId) {
        portfolioCache.remove(userId);
    }

    private void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        portfolioCache.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMs() < now);
    }

    private void evictOldestEntry() {
        Long oldestKey = null;
        long oldestTimestamp = Long.MAX_VALUE;
        for (Map.Entry<Long, CachedValue<PortfolioResponseDTO>> entry : portfolioCache.entrySet()) {
            long createdAt = entry.getValue().createdAtEpochMs();
            if (createdAt < oldestTimestamp) {
                oldestTimestamp = createdAt;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            portfolioCache.remove(oldestKey);
        }
    }

    private void validateRecordCompletedOrderRequest(Long userId, RecordPortfolioOrderRequestDTO request) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("Valid userId is required.");
        if (request == null || request.getItems() == null || request.getItems().isEmpty())
            throw new IllegalArgumentException("At least one portfolio item is required.");

        for (int index = 0; index < request.getItems().size(); index++) {
            PortfolioFillItemRequestDTO item = request.getItems().get(index);
            if (item == null) throw new IllegalArgumentException("Portfolio item at index " + index + " is missing.");
            if (item.getStockId() == null || item.getStockId().isBlank())
                throw new IllegalArgumentException("stockId is required for item at index " + index);
            if (item.getPrice() == null || item.getPrice().signum() <= 0)
                throw new IllegalArgumentException("price must be greater than 0 for stockId: " + item.getStockId());
            if (item.getQuantity() <= 0)
                throw new IllegalArgumentException("quantity must be greater than 0 for stockId: " + item.getStockId());
        }
    }

    private Long parseStockId(String stockId) {
        try {
            return Long.parseLong(stockId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid stockId format: " + stockId);
        }
    }


    private PortfolioAnalytics calculateAnalytics(List<PortfolioTransaction> transactions) {
        Map<Long, CostBasisState> stateByStock = new LinkedHashMap<>();
        Map<String, BigDecimal> realizedByTransactionId = new LinkedHashMap<>();

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
            averageBuyByStock.put(entry.getKey(), entry.getValue().averageCost());
            realizedByStock.put(entry.getKey(), PortfolioMapper.scaleMoney(entry.getValue().realizedPnl));
        }

        return new PortfolioAnalytics(averageBuyByStock, realizedByStock, realizedByTransactionId);
    }

    private static class CostBasisState {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal totalCost = BigDecimal.ZERO;
        private BigDecimal realizedPnl = BigDecimal.ZERO;

        private BigDecimal averageCost() {
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return totalCost.divide(quantity, 2, RoundingMode.HALF_UP);
        }
    }

    private record PortfolioAnalytics(
            Map<Long, BigDecimal> averageBuyByStock,
            Map<Long, BigDecimal> realizedByStock,
            Map<String, BigDecimal> realizedByTransactionId
    ) {}

    private PortfolioSummaryResponseDTO toSummary(List<PortfolioHoldingResponseDTO> holdings, PortfolioAnalytics analytics) {
        BigDecimal totalInvested = holdings.stream().map(PortfolioHoldingResponseDTO::getInvestedValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMarketValue = holdings.stream().map(PortfolioHoldingResponseDTO::getMarketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalUnrealized = holdings.stream().map(PortfolioHoldingResponseDTO::getUnrealizedPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRealized = analytics.realizedByStock().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalQuantity = holdings.stream().mapToInt(PortfolioHoldingResponseDTO::getQuantity).sum();

        BigDecimal totalUnrealizedPercent = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            totalUnrealizedPercent = totalUnrealized.divide(totalInvested, 6, RoundingMode.HALF_UP)
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

    private void publishAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record CachedValue<T>(T value, long expiresAtEpochMs, long createdAtEpochMs) {
        private CachedValue(T value, long expiresAtEpochMs) {
            this(value, expiresAtEpochMs, System.currentTimeMillis());
        }
    }
}

