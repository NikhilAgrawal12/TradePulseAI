package com.tradepulseai.orderservice.service;

import com.tradepulseai.orderservice.dto.AddCartItemRequestDTO;
import com.tradepulseai.orderservice.dto.CartItemResponseDTO;
import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.repository.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;

    public CartService(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional(readOnly = true)
    public List<CartItemResponseDTO> getCart(String userEmail) {
        return cartItemRepository.findByUserEmailOrderByUpdatedAtDesc(userEmail)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<CartItemResponseDTO> addToCart(String userEmail, AddCartItemRequestDTO request) {
        CartItem cartItem = cartItemRepository.findByUserEmailAndStockId(userEmail, request.getStockId())
                .orElseGet(CartItem::new);

        if (cartItem.getId() == null) {
            cartItem.setUserEmail(userEmail);
            cartItem.setStockId(request.getStockId());
            cartItem.setSymbol(request.getSymbol());
            cartItem.setPrice(request.getPrice());
            cartItem.setQuantity(request.getQuantity());
        } else {
            cartItem.setSymbol(request.getSymbol());
            cartItem.setPrice(request.getPrice());
            cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
        }

        cartItemRepository.save(cartItem);
        return getCart(userEmail);
    }

    @Transactional
    public List<CartItemResponseDTO> updateQuantity(String userEmail, String stockId, int quantity) {
        CartItem cartItem = cartItemRepository.findByUserEmailAndStockId(userEmail, stockId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found for stockId: " + stockId));

        cartItem.setQuantity(quantity);
        cartItemRepository.save(cartItem);
        return getCart(userEmail);
    }

    @Transactional
    public List<CartItemResponseDTO> removeFromCart(String userEmail, String stockId) {
        cartItemRepository.deleteByUserEmailAndStockId(userEmail, stockId);
        return getCart(userEmail);
    }

    @Transactional
    public List<CartItemResponseDTO> clearCart(String userEmail) {
        cartItemRepository.deleteByUserEmail(userEmail);
        return List.of();
    }

    private CartItemResponseDTO toResponse(CartItem cartItem) {
        CartItemResponseDTO response = new CartItemResponseDTO();
        response.setId(cartItem.getId());
        response.setStockId(cartItem.getStockId());
        response.setSymbol(cartItem.getSymbol());
        response.setPrice(cartItem.getPrice());
        response.setQuantity(cartItem.getQuantity());
        return response;
    }
}

