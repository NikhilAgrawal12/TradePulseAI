package com.tradepulseai.orderservice.mapper;

import com.tradepulseai.orderservice.dto.CartItemResponseDTO;
import com.tradepulseai.orderservice.model.CartItem;

public class CartItemMapper {

    public static CartItemResponseDTO toDTO(CartItem cartItem) {
        CartItemResponseDTO response = new CartItemResponseDTO();
        response.setId(cartItem.getId());
        response.setStockId(cartItem.getStockId());
        response.setSymbol(cartItem.getSymbol());
        response.setPrice(cartItem.getPrice());
        response.setQuantity(cartItem.getQuantity());
        return response;
    }

    public static CartItem toModel(CartItemResponseDTO dto) {
        CartItem cartItem = new CartItem();
        cartItem.setId(dto.getId());
        cartItem.setStockId(dto.getStockId());
        cartItem.setSymbol(dto.getSymbol());
        cartItem.setPrice(dto.getPrice());
        cartItem.setQuantity(dto.getQuantity());
        return cartItem;
    }
}

