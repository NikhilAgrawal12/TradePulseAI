package com.tradepulseai.custservice.service;

import com.tradepulseai.custservice.client.AuthServiceClient;
import com.tradepulseai.custservice.dto.AddWatchlistItemRequestDTO;
import com.tradepulseai.custservice.dto.UpdateWatchlistItemRequestDTO;
import com.tradepulseai.custservice.dto.WatchlistItemResponseDTO;
import com.tradepulseai.custservice.model.WatchlistItem;
import com.tradepulseai.custservice.model.WatchlistItemId;
import com.tradepulseai.custservice.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class WatchlistService {

    private final WatchlistItemRepository watchlistItemRepository;
    private final AuthServiceClient authServiceClient;

    public WatchlistService(WatchlistItemRepository watchlistItemRepository, AuthServiceClient authServiceClient) {
        this.watchlistItemRepository = watchlistItemRepository;
        this.authServiceClient = authServiceClient;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponseDTO> getWatchlist(String userEmail) {
        Long userId = authServiceClient.getUserByEmail(userEmail).userId();
        return watchlistItemRepository.findByIdUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<WatchlistItemResponseDTO> addToWatchlist(String userEmail, AddWatchlistItemRequestDTO request) {
        Long userId = authServiceClient.getUserByEmail(userEmail).userId();
        Long stockId = parseStockId(request.getStockId());

        WatchlistItem watchlistItem = watchlistItemRepository.findByIdUserIdAndIdStockId(userId, stockId)
                .map(existing -> {
                    existing.setQuantity(scaleQuantity(existing.getQuantity().add(request.getQuantity())));
                    return existing;
                })
                .orElseGet(() -> newWatchlistItem(userId, stockId, request.getQuantity()));


        watchlistItemRepository.save(watchlistItem);
        return getWatchlist(userEmail);
    }

    @Transactional
    public List<WatchlistItemResponseDTO> updateWatchlistItem(String userEmail, String stockId, UpdateWatchlistItemRequestDTO request) {
        Long userId = authServiceClient.getUserByEmail(userEmail).userId();
        Long parsedStockId = parseStockId(stockId);

        WatchlistItem watchlistItem = watchlistItemRepository.findByIdUserIdAndIdStockId(userId, parsedStockId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist item not found for stockId: " + stockId));

        watchlistItem.setQuantity(scaleQuantity(request.getQuantity()));
        watchlistItemRepository.save(watchlistItem);
        return getWatchlist(userEmail);
    }

    @Transactional
    public List<WatchlistItemResponseDTO> removeFromWatchlist(String userEmail, String stockId) {
        Long userId = authServiceClient.getUserByEmail(userEmail).userId();
        watchlistItemRepository.deleteByIdUserIdAndIdStockId(userId, parseStockId(stockId));
        return getWatchlist(userEmail);
    }

    @Transactional
    public List<WatchlistItemResponseDTO> clearWatchlist(String userEmail) {
        Long userId = authServiceClient.getUserByEmail(userEmail).userId();
        watchlistItemRepository.deleteByIdUserId(userId);
        return List.of();
    }

    private WatchlistItemResponseDTO toResponse(WatchlistItem watchlistItem) {
        WatchlistItemResponseDTO response = new WatchlistItemResponseDTO();
        response.setStockId(String.valueOf(watchlistItem.getId().getStockId()));
        response.setQuantity(watchlistItem.getQuantity());
        return response;
    }

    private WatchlistItem newWatchlistItem(Long userId, Long stockId, BigDecimal quantity) {
        WatchlistItem item = new WatchlistItem();
        WatchlistItemId id = new WatchlistItemId();
        id.setUserId(userId);
        id.setStockId(stockId);
        item.setId(id);
        item.setQuantity(scaleQuantity(quantity));
        return item;
    }

    private Long parseStockId(String stockId) {
        try {
            return Long.parseLong(stockId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid stockId format: " + stockId);
        }
    }

    private BigDecimal scaleQuantity(BigDecimal quantity) {
        return Objects.requireNonNullElse(quantity, BigDecimal.ZERO)
                .setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
