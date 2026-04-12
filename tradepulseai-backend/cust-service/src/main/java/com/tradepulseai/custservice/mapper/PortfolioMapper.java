package com.tradepulseai.custservice.mapper;

import com.tradepulseai.custservice.client.StockCatalogClient;
import com.tradepulseai.custservice.dto.PortfolioFillItemRequestDTO;
import com.tradepulseai.custservice.dto.PortfolioHoldingResponseDTO;
import com.tradepulseai.custservice.dto.PortfolioTransactionResponseDTO;
import com.tradepulseai.custservice.model.PortfolioHolding;
import com.tradepulseai.custservice.model.PortfolioHoldingId;
import com.tradepulseai.custservice.model.PortfolioTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

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
        return toHoldingResponse(
                holding,
                new StockCatalogClient.StockQuote(holding.getId().getStockId(), String.valueOf(holding.getId().getStockId()), BigDecimal.ZERO),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    public static PortfolioHoldingResponseDTO toHoldingResponse(
            PortfolioHolding holding,
            StockCatalogClient.StockQuote quote,
            BigDecimal averageBuyPrice,
            BigDecimal realizedPnl
    ) {
        BigDecimal scaledAverageBuy = scaleMoney(averageBuyPrice);
        BigDecimal currentPrice = scaleMoney(quote.unitPrice());
        BigDecimal quantity = scaleQuantity(holding.getTotalQuantity());
        BigDecimal investedValue = scaleMoney(scaledAverageBuy.multiply(quantity));
        BigDecimal marketValue = scaleMoney(currentPrice.multiply(quantity));
        BigDecimal unrealizedPnl = scaleMoney(marketValue.subtract(investedValue));
        BigDecimal unrealizedPnlPercent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (investedValue.compareTo(BigDecimal.ZERO) > 0) {
            unrealizedPnlPercent = unrealizedPnl
                    .divide(investedValue, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        PortfolioHoldingResponseDTO response = new PortfolioHoldingResponseDTO();
        response.setStockId(String.valueOf(holding.getId().getStockId()));
        response.setSymbol(quote.symbol());
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

    public static PortfolioTransactionResponseDTO toTransactionResponse(PortfolioTransaction transaction) {
        return toTransactionResponse(transaction, String.valueOf(transaction.getStockId()), BigDecimal.ZERO);
    }

    public static PortfolioTransactionResponseDTO toTransactionResponse(
            PortfolioTransaction transaction,
            String symbol,
            BigDecimal realizedPnl
    ) {
        PortfolioTransactionResponseDTO response = new PortfolioTransactionResponseDTO();
        response.setTransactionId(transaction.getTransactionId());
        response.setStockId(String.valueOf(transaction.getStockId()));
        response.setSymbol(symbol);
        response.setTransactionType(transaction.getTransactionType());
        response.setPrice(scaleMoney(transaction.getPrice()));
        response.setQuantity(transaction.getQuantity());
        response.setGrossAmount(calculateGrossAmount(transaction.getPrice(), transaction.getQuantity()));
        response.setRealizedPnl(scaleMoney(realizedPnl));
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
        return Objects.requireNonNullElse(value, BigDecimal.ZERO)
                .setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal scaleQuantity(BigDecimal value) {
        return scaleMoney(value);
    }
}
