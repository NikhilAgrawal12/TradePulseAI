package com.tradepulseai.orderservice.controller;

import com.tradepulseai.orderservice.dto.AddCartItemRequestDTO;
import com.tradepulseai.orderservice.dto.CartItemResponseDTO;
import com.tradepulseai.orderservice.dto.UpdateCartItemRequestDTO;
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

    private static final String USER_EMAIL_HEADER = "X-User-Email";

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Get current user's cart")
    public ResponseEntity<List<CartItemResponseDTO>> getCart(@RequestHeader(USER_EMAIL_HEADER) String userEmail) {
        return ResponseEntity.ok(cartService.getCart(normalizeUserEmail(userEmail)));
    }

    @PostMapping("/items")
    @Operation(summary = "Add stock to cart")
    public ResponseEntity<List<CartItemResponseDTO>> addToCart(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail,
            @Valid @RequestBody AddCartItemRequestDTO request
    ) {
        return ResponseEntity.ok(cartService.addToCart(normalizeUserEmail(userEmail), request));
    }

    @PutMapping("/items/{stockId}")
    @Operation(summary = "Update cart quantity for a stock")
    public ResponseEntity<List<CartItemResponseDTO>> updateQuantity(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail,
            @PathVariable String stockId,
            @Valid @RequestBody UpdateCartItemRequestDTO request
    ) {
        return ResponseEntity.ok(cartService.updateQuantity(normalizeUserEmail(userEmail), stockId, request.getQuantity()));
    }

    @DeleteMapping("/items/{stockId}")
    @Operation(summary = "Remove stock from cart")
    public ResponseEntity<List<CartItemResponseDTO>> removeFromCart(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail,
            @PathVariable String stockId
    ) {
        return ResponseEntity.ok(cartService.removeFromCart(normalizeUserEmail(userEmail), stockId));
    }

    @DeleteMapping
    @Operation(summary = "Clear cart")
    public ResponseEntity<List<CartItemResponseDTO>> clearCart(@RequestHeader(USER_EMAIL_HEADER) String userEmail) {
        return ResponseEntity.ok(cartService.clearCart(normalizeUserEmail(userEmail)));
    }

    private String normalizeUserEmail(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required header: " + USER_EMAIL_HEADER);
        }

        return userEmail.trim().toLowerCase();
    }
}


