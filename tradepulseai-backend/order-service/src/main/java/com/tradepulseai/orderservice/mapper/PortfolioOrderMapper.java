package com.tradepulseai.orderservice.mapper;

import com.tradepulseai.orderservice.dto.PortfolioOrderItemDTO;
import com.tradepulseai.orderservice.dto.PortfolioOrderSyncRequestDTO;
import com.tradepulseai.orderservice.model.CartItem;

import java.math.BigDecimal;
import java.util.List;

public class PortfolioOrderMapper {

    private PortfolioOrderMapper() {
    }

    public static PortfolioOrderSyncRequestDTO toSyncRequest(List<CartItem> cartItems) {
        PortfolioOrderSyncRequestDTO request = new PortfolioOrderSyncRequestDTO();
        request.setItems(
                cartItems.stream()
                        .map(PortfolioOrderMapper::toItem)
                        .toList()
        );
        return request;
    }

    private static PortfolioOrderItemDTO toItem(CartItem cartItem) {
        PortfolioOrderItemDTO item = new PortfolioOrderItemDTO();
        item.setStockId(String.valueOf(cartItem.getStockId()));
        item.setSymbol(cartItem.getSymbol());
        item.setPrice(cartItem.getPrice());
        item.setQuantity(toWholeNumberQuantity(cartItem.getQuantity(), cartItem.getStockId()));
        return item;
    }

    private static int toWholeNumberQuantity(BigDecimal quantity, Long stockId) {
        try {
            return quantity.intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Portfolio sync supports whole-number quantity only for stockId: " + stockId, exception);
        }
    }
}

