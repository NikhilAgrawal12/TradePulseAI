package com.tradepulseai.orderservice.controller;

import com.tradepulseai.orderservice.dto.OrderResponseDTO;
import com.tradepulseai.orderservice.service.OrderHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "API for reading order history")
public class OrderHistoryController {

    private static final String USER_EMAIL_HEADER = "X-User-Email";

    private final OrderHistoryService orderHistoryService;

    public OrderHistoryController(OrderHistoryService orderHistoryService) {
        this.orderHistoryService = orderHistoryService;
    }

    @GetMapping
    @Operation(summary = "Get full order history for current user")
    public ResponseEntity<List<OrderResponseDTO>> getOrders(@RequestHeader(USER_EMAIL_HEADER) String userEmail) {
        return ResponseEntity.ok(orderHistoryService.getOrders(normalizeUserEmail(userEmail)));
    }

    private String normalizeUserEmail(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required header: " + USER_EMAIL_HEADER);
        }

        String trimmedEmail = userEmail.trim().toLowerCase();
        if (!trimmedEmail.contains("@")) {
            throw new IllegalArgumentException("Invalid email format in header " + USER_EMAIL_HEADER + ": " + trimmedEmail);
        }

        return trimmedEmail;
    }
}

