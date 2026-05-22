package com.tradepulseai.orderservice.mapper;

import com.tradepulseai.orderservice.dto.portfolio.PortfolioOrderItemDTO;
import com.tradepulseai.orderservice.dto.portfolio.PortfolioOrderSyncRequestDTO;
import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.service.StockQuote;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class PortfolioOrderMapper {

    private PortfolioOrderMapper() {
    }

    public static PortfolioOrderSyncRequestDTO toSyncRequest(List<CartItem> cartItems, Map<Long, StockQuote> stockQuotes) {
        PortfolioOrderSyncRequestDTO request = new PortfolioOrderSyncRequestDTO();
        request.setItems(
                cartItems.stream()
                        .map(item -> toItem(item, resolveQuote(stockQuotes, item.getStockId())))
                        .toList()
        );
        return request;
    }


    private static PortfolioOrderItemDTO toItem(CartItem cartItem, StockQuote stockQuote) {
        PortfolioOrderItemDTO item = new PortfolioOrderItemDTO();
        item.setStockId(String.valueOf(cartItem.getStockId()));
        item.setSymbol(stockQuote.symbol());
        item.setPrice(stockQuote.unitPrice());
        item.setQuantity(toWholeNumberQuantity(cartItem.getQuantity(), cartItem.getStockId()));
        return item;
    }

    private static StockQuote resolveQuote(Map<Long, StockQuote> stockQuotes, Long stockId) {
        StockQuote quote = stockQuotes.get(stockId);
        if (quote == null) {
            throw new IllegalArgumentException("Missing stock quote for stockId: " + stockId);
        }
        return quote;
    }

    private static int toWholeNumberQuantity(BigDecimal quantity, Long stockId) {
        try {
            return quantity.intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Portfolio sync supports whole-number quantity only for stockId: " + stockId, exception);
        }
    }
}

