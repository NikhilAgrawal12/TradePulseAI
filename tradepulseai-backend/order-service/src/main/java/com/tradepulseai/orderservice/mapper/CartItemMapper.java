package com.tradepulseai.orderservice.mapper;

import com.tradepulseai.orderservice.dto.CartItemResponseDTO;
import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.model.CartItemId;

public class CartItemMapper {

    public static CartItemResponseDTO toDTO(CartItem cartItem) {
        CartItemResponseDTO response = new CartItemResponseDTO();
        response.setUserId(cartItem.getUserId());
        response.setStockId(String.valueOf(cartItem.getStockId()));
        response.setSymbol(cartItem.getSymbol());
        response.setPrice(cartItem.getPrice());
        response.setQuantity(cartItem.getQuantity());
        return response;
    }

    public static CartItem toModel(CartItemResponseDTO dto) {
        CartItem cartItem = new CartItem();
        CartItemId id = new CartItemId();
        id.setUserId(dto.getUserId());
        id.setStockId(Long.parseLong(dto.getStockId()));
        cartItem.setId(id);
        cartItem.setSymbol(dto.getSymbol());
        cartItem.setPrice(dto.getPrice());
        cartItem.setQuantity(dto.getQuantity());
        return cartItem;
    }
}

