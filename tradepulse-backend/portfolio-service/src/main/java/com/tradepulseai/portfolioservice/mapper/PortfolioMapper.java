package com.tradepulseai.portfolioservice.mapper;

import com.tradepulseai.portfolioservice.dto.PortfolioFillItemRequestDTO;
import com.tradepulseai.portfolioservice.dto.PortfolioHoldingResponseDTO;
import com.tradepulseai.portfolioservice.dto.PortfolioTransactionResponseDTO;
import com.tradepulseai.portfolioservice.model.PortfolioHolding;
import com.tradepulseai.portfolioservice.model.PortfolioHoldingId;
import com.tradepulseai.portfolioservice.model.PortfolioTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class PortfolioMapper {

    private PortfolioMapper() {}

    public static PortfolioHolding newHolding(Long userId, PortfolioFillItemRequestDTO request) {
        PortfolioHolding holding = new PortfolioHolding();
        PortfolioHoldingId id = new PortfolioHoldingId();
        id.setUserId(userId);
        id.setStockId(Long.parseLong(request.getStockId()));
        holding.setId(id);
        holding.setTotalQuantity(scaleQuantity(BigDecimal.valueOf(request.getQuantity())));
        holding.setAvgBuyPrice(scaleMoney(request.getPrice()));
        return holding;
    }

    public static PortfolioTransaction toBuyTransaction(Long userId, PortfolioFillItemRequestDTO request) {
        PortfolioTransaction transaction = new PortfolioTransaction();
        transaction.setUserId(userId);
        transaction.setStockId(Long.parseLong(request.getStockId()));
        transaction.setTransactionType("BUY");
        transaction.setPrice(scaleMoney(request.getPrice()));
        transaction.setQuantity(scaleQuantity(BigDecimal.valueOf(request.getQuantity())));
        return transaction;
    }

    public static PortfolioTransaction toSellTransaction(Long userId, Long stockId, int quantity, BigDecimal price) {
        PortfolioTransaction transaction = new PortfolioTransaction();
        transaction.setUserId(userId);
        transaction.setStockId(stockId);
        transaction.setTransactionType("SELL");
        transaction.setPrice(scaleMoney(price));
        transaction.setQuantity(scaleQuantity(BigDecimal.valueOf(quantity)));
        return transaction;
    }

    public static PortfolioHoldingResponseDTO toHoldingResponse(
            PortfolioHolding holding,
            BigDecimal averageBuyPrice,
            BigDecimal realizedPnl
    ) {
        BigDecimal scaledAverageBuy = scaleMoney(averageBuyPrice);
        BigDecimal currentPrice = scaledAverageBuy;
        BigDecimal quantity = scaleQuantity(holding.getTotalQuantity());
        BigDecimal investedValue = scaleMoney(scaledAverageBuy.multiply(quantity));
        BigDecimal marketValue = scaleMoney(currentPrice.multiply(quantity));
        BigDecimal unrealizedPnl = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal unrealizedPnlPercent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        PortfolioHoldingResponseDTO response = new PortfolioHoldingResponseDTO();
        response.setStockId(String.valueOf(holding.getId().getStockId()));
        response.setQuantity(holding.getTotalQuantity().intValue());
        response.setAverageBuyPrice(scaledAverageBuy);
        response.setCurrentPrice(currentPrice);
        response.setInvestedValue(investedValue);
        response.setMarketValue(marketValue);
        response.setUnrealizedPnl(unrealizedPnl);
        response.setUnrealizedPnlPercent(unrealizedPnlPercent);
        response.setRealizedPnl(scaleMoney(realizedPnl));
        return response;
    }

    public static PortfolioTransactionResponseDTO toTransactionResponse(
            PortfolioTransaction transaction,
            BigDecimal realizedPnl
    ) {
        PortfolioTransactionResponseDTO response = new PortfolioTransactionResponseDTO();
        response.setTransactionId(transaction.getTransactionId());
        response.setStockId(String.valueOf(transaction.getStockId()));
        response.setTransactionType(transaction.getTransactionType());
        response.setPrice(scaleMoney(transaction.getPrice()));
        response.setQuantity(transaction.getQuantity());
        response.setGrossAmount(scaleMoney(transaction.getPrice().multiply(transaction.getQuantity())));
        response.setRealizedPnl(scaleMoney(realizedPnl));
        response.setExecutedAt(transaction.getExecutedAt());
        return response;
    }

    public static BigDecimal scaleMoney(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal scaleQuantity(BigDecimal value) {
        return scaleMoney(value);
    }
}

