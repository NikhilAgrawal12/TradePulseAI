package com.tradepulseai.orderservice.mapper;

import com.tradepulseai.orderservice.dto.order.OrderItemResponseDTO;
import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.model.TradeOrder;
import com.tradepulseai.orderservice.model.TradeOrderItem;
import com.tradepulseai.orderservice.service.StockQuote;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class OrderItemMapper {

    private OrderItemMapper() {
    }

    public static TradeOrderItem toModel(TradeOrder order, CartItem cartItem, StockQuote stockQuote) {
        TradeOrderItem item = new TradeOrderItem();
        item.setOrder(order);
        item.setStockId(cartItem.getStockId());
        item.setPrice(scaleMoney(stockQuote.unitPrice()));
        item.setQuantity(scaleQuantity(cartItem.getQuantity()));
        return item;
    }

    public static TradeOrderItem toModel(TradeOrder order, CartItem cartItem) {
        return toModel(
                order,
                cartItem,
                new StockQuote(cartItem.getStockId(), String.valueOf(cartItem.getStockId()), BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
        );
    }

    public static OrderItemResponseDTO toDTO(TradeOrderItem item) {
        OrderItemResponseDTO dto = new OrderItemResponseDTO();
        dto.setStockId(String.valueOf(item.getStockId()));
        dto.setPrice(scaleMoney(item.getPrice()));
        dto.setQuantityValue(scaleQuantity(item.getQuantity()));
        dto.setLineTotal(scaleMoney(item.getPrice().multiply(item.getQuantity())));
        return dto;
    }

    public static BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleQuantity(BigDecimal quantity) {
        return quantity.setScale(4, RoundingMode.HALF_UP);
    }
}

