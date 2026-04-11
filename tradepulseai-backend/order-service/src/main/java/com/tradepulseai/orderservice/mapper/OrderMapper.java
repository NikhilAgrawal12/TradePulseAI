package com.tradepulseai.orderservice.mapper;

import com.tradepulseai.orderservice.dto.OrderResponseDTO;
import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.model.TradeOrder;

import java.math.BigDecimal;
import java.util.List;

public class OrderMapper {

    private OrderMapper() {
    }

    public static TradeOrder toModel(Long userId, String status, List<CartItem> cartItems) {
        TradeOrder order = new TradeOrder();
        order.setUserId(userId);
        order.setStatus(status);

        BigDecimal subtotal = cartItems.stream()
                .map(item -> item.getPrice().multiply(item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal scaledSubtotal = OrderItemMapper.scaleMoney(subtotal);
        BigDecimal tax = OrderItemMapper.scaleMoney(scaledSubtotal.multiply(BigDecimal.valueOf(0.08)));
        BigDecimal total = OrderItemMapper.scaleMoney(scaledSubtotal.add(tax));

        order.setSubtotal(scaledSubtotal);
        order.setTax(tax);
        order.setTotal(total);
        order.setItems(
                cartItems.stream()
                        .map(item -> OrderItemMapper.toModel(order, item))
                        .toList()
        );

        return order;
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
}

