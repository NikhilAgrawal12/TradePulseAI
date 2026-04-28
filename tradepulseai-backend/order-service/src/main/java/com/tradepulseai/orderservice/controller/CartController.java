package com.tradepulseai.orderservice.controller;

import com.tradepulseai.orderservice.dto.cart.AddCartItemRequestDTO;
import com.tradepulseai.orderservice.dto.cart.CartItemResponseDTO;
import com.tradepulseai.orderservice.dto.cart.UpdateCartItemRequestDTO;
import com.tradepulseai.orderservice.dto.order.CompleteOrderResponseDTO;
import com.tradepulseai.orderservice.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cart")
@Tag(name = "Cart", description = "API for managing user cart")
public class CartController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Get current user's cart")
    public ResponseEntity<List<CartItemResponseDTO>> getCart(@RequestHeader(USER_ID_HEADER) String userId) {
        return ResponseEntity.ok(cartService.getCart(normalizeUserId(userId)));
    }

    @PostMapping("/items")
    @Operation(summary = "Add stock to cart")
    public ResponseEntity<List<CartItemResponseDTO>> addToCart(
            @RequestHeader(USER_ID_HEADER) String userId,
            @Valid @RequestBody AddCartItemRequestDTO request
    ) {
        return ResponseEntity.ok(cartService.addToCart(normalizeUserId(userId), request));
    }

    @PutMapping("/items/{stockId}")
    @Operation(summary = "Update cart quantity for a stock")
    public ResponseEntity<List<CartItemResponseDTO>> updateQuantity(
            @RequestHeader(USER_ID_HEADER) String userId,
            @PathVariable String stockId,
            @Valid @RequestBody UpdateCartItemRequestDTO request
    ) {
        return ResponseEntity.ok(cartService.updateQuantity(normalizeUserId(userId), stockId, request.getQuantity()));
    }

    @DeleteMapping("/items/{stockId}")
    @Operation(summary = "Remove stock from cart")
    public ResponseEntity<List<CartItemResponseDTO>> removeFromCart(
            @RequestHeader(USER_ID_HEADER) String userId,
            @PathVariable String stockId
    ) {
        return ResponseEntity.ok(cartService.removeFromCart(normalizeUserId(userId), stockId));
    }

    @DeleteMapping
    @Operation(summary = "Clear cart")
    public ResponseEntity<List<CartItemResponseDTO>> clearCart(@RequestHeader(USER_ID_HEADER) String userId) {
        return ResponseEntity.ok(cartService.clearCart(normalizeUserId(userId)));
    }

    @PostMapping("/complete-order")
    @Operation(summary = "Complete order by processing payment for all cart items")
    public ResponseEntity<CompleteOrderResponseDTO> completeOrder(
            @RequestHeader(USER_ID_HEADER) String userId
    ) {
        Long normalizedUserId = normalizeUserId(userId);
        return ResponseEntity.ok(cartService.completeOrder(normalizedUserId));
    }

    private Long normalizeUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Missing required header: %s. Please ensure you are logged in.", USER_ID_HEADER)
            );
        }

        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    String.format("Invalid userId format in header %s: %s", USER_ID_HEADER, userId)
            );
        }
    }

}

