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
        item.setSymbol(cartItem.getSymbol());
        item.setPrice(scaleMoney(cartItem.getPrice()));
        item.setQuantity(cartItem.getQuantity());
        item.setLineTotal(scaleMoney(cartItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()))));
        return item;
    }

    public static OrderItemResponseDTO toDTO(TradeOrderItem item) {
        OrderItemResponseDTO dto = new OrderItemResponseDTO();
        dto.setStockId(item.getStockId());
        dto.setSymbol(item.getSymbol());
        dto.setPrice(scaleMoney(item.getPrice()));
        dto.setQuantity(item.getQuantity());
        dto.setLineTotal(scaleMoney(item.getLineTotal()));
        return dto;
    }

    public static BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}

