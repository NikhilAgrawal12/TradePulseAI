package com.tradepulseai.orderservice.mapper;

import com.tradepulseai.orderservice.dto.OrderItemResponseDTO;
import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.model.TradeOrderItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class OrderItemMapper {

    private OrderItemMapper() {
    }

    public static TradeOrderItem toModel(TradeOrder order, CartItem cartItem) {
        TradeOrderItem item = new TradeOrderItem();
        item.setOrder(order);
        item.setStockId(cartItem.getStockId());
        item.setPrice(scaleMoney(cartItem.getPrice()));
        item.setQuantity(scaleQuantity(cartItem.getQuantity()));
        return item;
    }

    public static OrderItemResponseDTO toDTO(TradeOrderItem item) {
        OrderItemResponseDTO dto = new OrderItemResponseDTO();
        dto.setStockId(String.valueOf(item.getStockId()));
        dto.setPrice(scaleMoney(item.getPrice()));
        dto.setQuantityValue(scaleQuantity(item.getQuantity()));
        return dto;
    }

    public static BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleQuantity(BigDecimal quantity) {
        return quantity.setScale(4, RoundingMode.HALF_UP);
    }
}

