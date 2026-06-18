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
        item.setStockId(String.valueOf(cartItem.getStockId()));
        item.setPrice(scaleMoney(stockQuote.unitPrice()));
        item.setQuantity(scaleQuantity(cartItem.getQuantity()));
        return item;
    }


    public static OrderItemResponseDTO toDTO(TradeOrderItem item) {
        OrderItemResponseDTO dto = new OrderItemResponseDTO();
        dto.setStockId(item.getStockId());
        dto.setPrice(scaleMoney(item.getPrice()));
        dto.setQuantity(item.getQuantity());
        dto.setLineTotal(scaleMoney(item.getPrice().multiply(item.getQuantity())));
        return dto;
    }

    public static BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleQuantity(BigDecimal quantity) {
        return quantity.setScale(8, RoundingMode.HALF_UP);
    }
}
