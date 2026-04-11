package com.tradepulseai.custservice.mapper;

import com.tradepulseai.custservice.dto.PortfolioFillItemRequestDTO;
import com.tradepulseai.custservice.dto.PortfolioHoldingResponseDTO;
import com.tradepulseai.custservice.dto.PortfolioTransactionResponseDTO;
import com.tradepulseai.custservice.model.PortfolioHolding;
import com.tradepulseai.custservice.model.PortfolioHoldingId;
import com.tradepulseai.custservice.model.PortfolioTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PortfolioMapper {

    private PortfolioMapper() {
    }

    public static PortfolioHolding newHolding(Long userId, PortfolioFillItemRequestDTO request) {
        PortfolioHolding holding = new PortfolioHolding();
        PortfolioHoldingId id = new PortfolioHoldingId();
        id.setUserId(userId);
        id.setStockId(Long.parseLong(request.getStockId()));
        holding.setId(id);
        holding.setTotalQuantity(scaleQuantity(BigDecimal.valueOf(request.getQuantity())));
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

    public static PortfolioTransaction toSellTransaction(
            Long userId,
            Long stockId,
            int quantity,
            BigDecimal price
    ) {
        PortfolioTransaction transaction = new PortfolioTransaction();
        transaction.setUserId(userId);
        transaction.setStockId(stockId);
        transaction.setTransactionType("SELL");
        transaction.setPrice(scaleMoney(price));
        transaction.setQuantity(scaleQuantity(BigDecimal.valueOf(quantity)));
        return transaction;
    }

    public static PortfolioHoldingResponseDTO toHoldingResponse(PortfolioHolding holding) {
        BigDecimal marketValue = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal unrealizedPnl = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal unrealizedPnlPercent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        PortfolioHoldingResponseDTO response = new PortfolioHoldingResponseDTO();
        response.setStockId(String.valueOf(holding.getId().getStockId()));
        response.setSymbol(String.valueOf(holding.getId().getStockId()));
        response.setQuantity(holding.getTotalQuantity().intValue());
        response.setAverageBuyPrice(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        response.setCurrentPrice(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        response.setInvestedValue(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        response.setMarketValue(scaleMoney(marketValue));
        response.setUnrealizedPnl(scaleMoney(unrealizedPnl));
        response.setUnrealizedPnlPercent(unrealizedPnlPercent);
        response.setRealizedPnl(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        return response;
    }

    public static PortfolioTransactionResponseDTO toTransactionResponse(PortfolioTransaction transaction) {
        PortfolioTransactionResponseDTO response = new PortfolioTransactionResponseDTO();
        response.setTransactionId(transaction.getTransactionId());
        response.setStockId(String.valueOf(transaction.getStockId()));
        response.setSymbol(String.valueOf(transaction.getStockId()));
        response.setTransactionType(transaction.getTransactionType());
        response.setPrice(scaleMoney(transaction.getPrice()));
        response.setQuantity(transaction.getQuantity());
        response.setGrossAmount(calculateGrossAmount(transaction.getPrice(), transaction.getQuantity()));
        response.setRealizedPnl(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        response.setExecutedAt(transaction.getExecutedAt());
        return response;
    }

    public static BigDecimal calculateGrossAmount(BigDecimal price, int quantity) {
        return scaleMoney(price.multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal calculateGrossAmount(BigDecimal price, BigDecimal quantity) {
        return scaleMoney(price.multiply(quantity));
    }

    public static BigDecimal scaleMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal scaleQuantity(BigDecimal value) {
        return scaleMoney(value);
    }
}
