package com.tradepulseai.custservice.controller;

import com.tradepulseai.custservice.dto.PortfolioResponseDTO;
import com.tradepulseai.custservice.dto.RecordPortfolioOrderRequestDTO;
import com.tradepulseai.custservice.dto.SellPortfolioItemRequestDTO;
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

    private static final String USER_EMAIL_HEADER = "X-User-Email";

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    @Operation(summary = "Get portfolio summary, holdings and transaction history")
    public ResponseEntity<PortfolioResponseDTO> getPortfolio(@RequestHeader(USER_EMAIL_HEADER) String userEmail) {
        return ResponseEntity.ok(portfolioService.getPortfolio(normalizeUserEmail(userEmail)));
    }

    @PostMapping("/orders/complete")
    @Operation(summary = "Record completed buy order into portfolio")
    public ResponseEntity<PortfolioResponseDTO> recordCompletedOrder(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail,
            @Valid @RequestBody RecordPortfolioOrderRequestDTO request
    ) {
        return ResponseEntity.ok(portfolioService.recordCompletedOrder(normalizeUserEmail(userEmail), request));
    }

    @PostMapping("/sell/{stockId}")
    @Operation(summary = "Sell a stock from portfolio")
    public ResponseEntity<PortfolioResponseDTO> sellPosition(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail,
            @PathVariable String stockId,
            @Valid @RequestBody SellPortfolioItemRequestDTO request
    ) {
        return ResponseEntity.ok(portfolioService.sellPosition(normalizeUserEmail(userEmail), stockId, request));
    }

    private String normalizeUserEmail(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required header: " + USER_EMAIL_HEADER);
        }

        return userEmail.trim().toLowerCase();
    }
}

