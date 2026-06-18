package com.tradepulseai.orderservice.mapper;

import com.tradepulseai.orderservice.dto.order.OrderResponseDTO;
import com.tradepulseai.orderservice.dto.order.CompleteOrderItemRequestDTO;
import com.tradepulseai.orderservice.dto.order.CompleteOrderRequestDTO;
import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.service.StockQuote;

import java.math.BigDecimal;
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

    public static TradeOrder toModel(Long userId, String status, CompleteOrderRequestDTO request) {
        TradeOrder order = new TradeOrder();
        order.setUserId(userId);
        order.setStatus(status);

        BigDecimal scaledSubtotal = OrderItemMapper.scaleMoney(request.getSubtotal());
        BigDecimal tax = OrderItemMapper.scaleMoney(request.getTax());
        BigDecimal total = OrderItemMapper.scaleMoney(request.getTotal());

        order.setSubtotal(scaledSubtotal);
        order.setTax(tax);
        order.setTotal(total);
        order.setItems(
                request.getItems().stream()
                        .map(item -> toModel(order, item))
                        .toList()
        );

        return order;
    }


    public static OrderResponseDTO toDTO(TradeOrder order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
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

    private static com.tradepulseai.orderservice.model.TradeOrderItem toModel(TradeOrder order, CompleteOrderItemRequestDTO itemRequest) {
        com.tradepulseai.orderservice.model.TradeOrderItem item = new com.tradepulseai.orderservice.model.TradeOrderItem();
        item.setOrder(order);
        item.setStockId(itemRequest.getStockId());
        item.setPrice(OrderItemMapper.scaleMoney(itemRequest.getPrice()));
        item.setQuantity(itemRequest.getQuantity().setScale(8, java.math.RoundingMode.HALF_UP));
        return item;
    }
}

