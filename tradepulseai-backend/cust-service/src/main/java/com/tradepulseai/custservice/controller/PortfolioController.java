package com.tradepulseai.custservice.controller;

import com.tradepulseai.custservice.dto.portfolio.PortfolioResponseDTO;
import com.tradepulseai.custservice.dto.portfolio.RecordPortfolioOrderRequestDTO;
import com.tradepulseai.custservice.dto.portfolio.SellPortfolioItemRequestDTO;
import com.tradepulseai.custservice.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers/portfolio")
@Tag(name = "Portfolio", description = "API for managing portfolio holdings and sell operations")
public class PortfolioController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    @Operation(summary = "Get portfolio summary, holdings and transaction history")
    public ResponseEntity<PortfolioResponseDTO> getPortfolio(@RequestHeader(USER_ID_HEADER) String userId) {
        return ResponseEntity.ok(portfolioService.getPortfolio(normalizeUserId(userId)));
    }

    @PostMapping("/orders/complete")
    @Operation(summary = "Record completed buy order into portfolio")
    public ResponseEntity<PortfolioResponseDTO> recordCompletedOrder(
            @RequestHeader(USER_ID_HEADER) String userId,
            @Valid @RequestBody RecordPortfolioOrderRequestDTO request
    ) {
        return ResponseEntity.ok(portfolioService.recordCompletedOrder(normalizeUserId(userId), request));
    }

    @PostMapping("/sell/{stockId}")
    @Operation(summary = "Sell a stock from portfolio")
    public ResponseEntity<PortfolioResponseDTO> sellPosition(
            @RequestHeader(USER_ID_HEADER) String userId,
            @PathVariable String stockId,
            @Valid @RequestBody SellPortfolioItemRequestDTO request
    ) {
        return ResponseEntity.ok(portfolioService.sellPosition(normalizeUserId(userId), stockId, request));
    }

    private Long normalizeUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required header: " + USER_ID_HEADER);
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid userId format: " + userId);
        }
    }
}

