package com.tradepulseai.custservice.mapper;

import com.tradepulseai.custservice.dto.PortfolioFillItemRequestDTO;
import com.tradepulseai.custservice.dto.PortfolioHoldingResponseDTO;
import com.tradepulseai.custservice.dto.PortfolioTransactionResponseDTO;
import com.tradepulseai.custservice.model.PortfolioHolding;
import com.tradepulseai.custservice.model.PortfolioTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PortfolioMapper {

    private PortfolioMapper() {
    }

    public static PortfolioHolding newHolding(String userEmail, PortfolioFillItemRequestDTO request) {
        PortfolioHolding holding = new PortfolioHolding();
        holding.setUserEmail(userEmail);
        holding.setStockId(request.getStockId());
        holding.setSymbol(request.getSymbol());
        holding.setTotalQuantity(request.getQuantity());
        holding.setAverageBuyPrice(scaleMoney(request.getPrice()));
        holding.setInvestedAmount(calculateGrossAmount(request.getPrice(), request.getQuantity()));
        holding.setCurrentMarketPrice(scaleMoney(request.getPrice()));
        holding.setRealizedPnl(BigDecimal.ZERO);
        return holding;
    }

    public static PortfolioTransaction toBuyTransaction(String userEmail, PortfolioFillItemRequestDTO request) {
        PortfolioTransaction transaction = new PortfolioTransaction();
        transaction.setUserEmail(userEmail);
        transaction.setStockId(request.getStockId());
        transaction.setSymbol(request.getSymbol());
        transaction.setTransactionType("BUY");
        transaction.setPrice(scaleMoney(request.getPrice()));
        transaction.setQuantity(request.getQuantity());
        transaction.setGrossAmount(calculateGrossAmount(request.getPrice(), request.getQuantity()));
        transaction.setRealizedPnl(BigDecimal.ZERO);
        return transaction;
    }

    public static PortfolioTransaction toSellTransaction(
            String userEmail,
            String stockId,
            String symbol,
            int quantity,
            BigDecimal price,
            BigDecimal realizedPnl
    ) {
        PortfolioTransaction transaction = new PortfolioTransaction();
        transaction.setUserEmail(userEmail);
        transaction.setStockId(stockId);
        transaction.setSymbol(symbol);
        transaction.setTransactionType("SELL");
        transaction.setPrice(scaleMoney(price));
        transaction.setQuantity(quantity);
        transaction.setGrossAmount(calculateGrossAmount(price, quantity));
        transaction.setRealizedPnl(scaleMoney(realizedPnl));
        return transaction;
    }

    public static PortfolioHoldingResponseDTO toHoldingResponse(PortfolioHolding holding) {
        BigDecimal marketValue = calculateGrossAmount(holding.getCurrentMarketPrice(), holding.getTotalQuantity());
        BigDecimal unrealizedPnl = scaleMoney(marketValue.subtract(holding.getInvestedAmount()));
        BigDecimal unrealizedPnlPercent = BigDecimal.ZERO;

        if (holding.getInvestedAmount().compareTo(BigDecimal.ZERO) > 0) {
            unrealizedPnlPercent = unrealizedPnl
                    .divide(holding.getInvestedAmount(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        PortfolioHoldingResponseDTO response = new PortfolioHoldingResponseDTO();
        response.setId(holding.getId());
        response.setStockId(holding.getStockId());
        response.setSymbol(holding.getSymbol());
        response.setQuantity(holding.getTotalQuantity());
        response.setAverageBuyPrice(scaleMoney(holding.getAverageBuyPrice()));
        response.setCurrentPrice(scaleMoney(holding.getCurrentMarketPrice()));
        response.setInvestedValue(scaleMoney(holding.getInvestedAmount()));
        response.setMarketValue(scaleMoney(marketValue));
        response.setUnrealizedPnl(scaleMoney(unrealizedPnl));
        response.setUnrealizedPnlPercent(unrealizedPnlPercent.setScale(2, RoundingMode.HALF_UP));
        response.setRealizedPnl(scaleMoney(holding.getRealizedPnl()));
        return response;
    }

    public static PortfolioTransactionResponseDTO toTransactionResponse(PortfolioTransaction transaction) {
        PortfolioTransactionResponseDTO response = new PortfolioTransactionResponseDTO();
        response.setId(transaction.getId());
        response.setStockId(transaction.getStockId());
        response.setSymbol(transaction.getSymbol());
        response.setTransactionType(transaction.getTransactionType());
        response.setPrice(scaleMoney(transaction.getPrice()));
        response.setQuantity(transaction.getQuantity());
        response.setGrossAmount(scaleMoney(transaction.getGrossAmount()));
        response.setRealizedPnl(scaleMoney(transaction.getRealizedPnl()));
        response.setExecutedAt(transaction.getExecutedAt());
        return response;
    }

    public static BigDecimal calculateGrossAmount(BigDecimal price, int quantity) {
        return scaleMoney(price.multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal scaleMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}

