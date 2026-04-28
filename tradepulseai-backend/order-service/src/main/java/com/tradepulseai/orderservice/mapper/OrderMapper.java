package com.tradepulseai.orderservice.mapper;

import com.tradepulseai.orderservice.dto.order.OrderResponseDTO;
import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.service.StockQuote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderMapper {

    private OrderMapper() {
    }

    public static TradeOrder toModel(Long userId, String status, List<CartItem> cartItems, Map<Long, StockQuote> stockQuotes) {
        TradeOrder order = new TradeOrder();
        order.setUserId(userId);
        order.setStatus(status);

        BigDecimal subtotal = cartItems.stream()
                .map(item -> resolveQuote(stockQuotes, item.getStockId()).unitPrice().multiply(item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal scaledSubtotal = OrderItemMapper.scaleMoney(subtotal);
        BigDecimal tax = OrderItemMapper.scaleMoney(scaledSubtotal.multiply(BigDecimal.valueOf(0.08)));
        BigDecimal total = OrderItemMapper.scaleMoney(scaledSubtotal.add(tax));

        order.setSubtotal(scaledSubtotal);
        order.setTax(tax);
        order.setTotal(total);
        order.setItems(
                cartItems.stream()
                        .map(item -> OrderItemMapper.toModel(order, item, resolveQuote(stockQuotes, item.getStockId())))
                        .toList()
        );

        return order;
    }

    public static TradeOrder toModel(Long userId, String status, List<CartItem> cartItems) {
        Map<Long, StockQuote> fallbackQuotes = new LinkedHashMap<>();
        for (CartItem item : cartItems) {
            fallbackQuotes.putIfAbsent(
                    item.getStockId(),
                    new StockQuote(item.getStockId(), String.valueOf(item.getStockId()), BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
            );
        }
        return toModel(userId, status, cartItems, fallbackQuotes);
    }

    public static OrderResponseDTO toDTO(TradeOrder order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.getId());
        dto.setUserId(order.getUserId());
        dto.setStatus(order.getStatus());
        dto.setCreatedAtIso(order.getCreatedAt());
        dto.setSubtotal(OrderItemMapper.scaleMoney(order.getSubtotal()));
        dto.setTax(OrderItemMapper.scaleMoney(order.getTax()));
        dto.setTotal(OrderItemMapper.scaleMoney(order.getTotal()));
        dto.setItems(order.getItems().stream().map(OrderItemMapper::toDTO).toList());
        return dto;
    }

    private static StockQuote resolveQuote(Map<Long, StockQuote> stockQuotes, Long stockId) {
        StockQuote quote = stockQuotes.get(stockId);
        if (quote == null) {
            throw new IllegalArgumentException("Missing stock quote for stockId: " + stockId);
        }
        return quote;
    }
}

