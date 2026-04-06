package com.tradepulseai.custservice.controller;

import com.tradepulseai.custservice.dto.AddWatchlistItemRequestDTO;
import com.tradepulseai.custservice.dto.UpdateWatchlistItemRequestDTO;
import com.tradepulseai.custservice.dto.WatchlistItemResponseDTO;
import com.tradepulseai.custservice.service.WatchlistService;
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
@RequestMapping("/watchlist")
@Tag(name = "Watchlist", description = "API for managing user watchlist")
public class WatchlistController {

    private static final String USER_EMAIL_HEADER = "X-User-Email";

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    @Operation(summary = "Get current user's watchlist")
    public ResponseEntity<List<WatchlistItemResponseDTO>> getWatchlist(@RequestHeader(USER_EMAIL_HEADER) String userEmail) {
        return ResponseEntity.ok(watchlistService.getWatchlist(normalizeUserEmail(userEmail)));
    }

    @PostMapping("/items")
    @Operation(summary = "Add stock to watchlist")
    public ResponseEntity<List<WatchlistItemResponseDTO>> addToWatchlist(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail,
            @Valid @RequestBody AddWatchlistItemRequestDTO request
    ) {
        return ResponseEntity.ok(watchlistService.addToWatchlist(normalizeUserEmail(userEmail), request));
    }

    @PutMapping("/items/{stockId}")
    @Operation(summary = "Update watchlist entry")
    public ResponseEntity<List<WatchlistItemResponseDTO>> updateWatchlistItem(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail,
            @PathVariable String stockId,
            @Valid @RequestBody UpdateWatchlistItemRequestDTO request
    ) {
        return ResponseEntity.ok(watchlistService.updateWatchlistItem(normalizeUserEmail(userEmail), stockId, request));
    }

    @DeleteMapping("/items/{stockId}")
    @Operation(summary = "Remove stock from watchlist")
    public ResponseEntity<List<WatchlistItemResponseDTO>> removeFromWatchlist(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail,
            @PathVariable String stockId
    ) {
        return ResponseEntity.ok(watchlistService.removeFromWatchlist(normalizeUserEmail(userEmail), stockId));
    }

    @DeleteMapping
    @Operation(summary = "Clear watchlist")
    public ResponseEntity<List<WatchlistItemResponseDTO>> clearWatchlist(@RequestHeader(USER_EMAIL_HEADER) String userEmail) {
        return ResponseEntity.ok(watchlistService.clearWatchlist(normalizeUserEmail(userEmail)));
    }

    private String normalizeUserEmail(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required header: " + USER_EMAIL_HEADER);
        }

        return userEmail.trim().toLowerCase();
    }
}

