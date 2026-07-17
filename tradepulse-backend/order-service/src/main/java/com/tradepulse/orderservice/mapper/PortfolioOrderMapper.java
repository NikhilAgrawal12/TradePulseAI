package com.tradepulse.orderservice.mapper;

import com.tradepulse.orderservice.dto.portfolio.PortfolioOrderItemDTO;
import com.tradepulse.orderservice.dto.portfolio.PortfolioOrderSyncRequestDTO;
import com.tradepulse.orderservice.dto.order.CompleteOrderItemRequestDTO;
import com.tradepulse.orderservice.model.CartItem;
import com.tradepulse.orderservice.model.TradeOrder;
import com.tradepulse.orderservice.model.TradeOrderItem;
import com.tradepulse.orderservice.service.StockQuote;

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

    public static PortfolioOrderSyncRequestDTO toSyncRequestFromPayment(List<CompleteOrderItemRequestDTO> items) {
        PortfolioOrderSyncRequestDTO request = new PortfolioOrderSyncRequestDTO();
        request.setItems(
                items.stream()
                        .map(item -> {
                            PortfolioOrderItemDTO dto = new PortfolioOrderItemDTO();
                            dto.setStockId(item.getStockId());
                            dto.setPrice(item.getPrice() == null ? null : item.getPrice().setScale(2, java.math.RoundingMode.HALF_UP));
                            dto.setQuantity(toWholeNumberQuantity(item.getQuantity(), item.getStockId()));
                            return dto;
                        })
                        .toList()
        );
        return request;
    }

    public static PortfolioOrderSyncRequestDTO toSyncRequestFromOrder(TradeOrder order) {
        PortfolioOrderSyncRequestDTO request = new PortfolioOrderSyncRequestDTO();
        request.setItems(
                order.getItems().stream()
                        .map(PortfolioOrderMapper::toItem)
                        .toList()
        );
        return request;
    }


    private static PortfolioOrderItemDTO toItem(CartItem cartItem, StockQuote stockQuote) {
        PortfolioOrderItemDTO item = new PortfolioOrderItemDTO();
        item.setStockId(String.valueOf(cartItem.getStockId()));
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
            return quantity.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Portfolio sync supports whole-number quantity only for stockId: " + stockId, exception);
        }
    }

    private static PortfolioOrderItemDTO toItem(TradeOrderItem orderItem) {
        PortfolioOrderItemDTO item = new PortfolioOrderItemDTO();
        item.setStockId(orderItem.getStockId());
        item.setPrice(orderItem.getPrice());
        item.setQuantity(toWholeNumberQuantity(orderItem.getQuantity(), orderItem.getStockId()));
        return item;
    }

    private static int toWholeNumberQuantity(BigDecimal quantity, String stockId) {
        try {
            return quantity.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Portfolio sync supports whole-number quantity only for stockId: " + stockId, exception);
        }
    }
}

