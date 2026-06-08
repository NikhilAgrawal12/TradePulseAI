package com.tradepulseai.custservice.service;

import com.tradepulseai.custservice.dto.watchlist.AddWatchlistItemRequestDTO;
import com.tradepulseai.custservice.dto.watchlist.WatchlistItemResponseDTO;
import com.tradepulseai.custservice.model.WatchlistItem;
import com.tradepulseai.custservice.model.WatchlistItemId;
import com.tradepulseai.custservice.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WatchlistService {

    private final WatchlistItemRepository watchlistItemRepository;

    public WatchlistService(WatchlistItemRepository watchlistItemRepository) {
        this.watchlistItemRepository = watchlistItemRepository;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponseDTO> getWatchlist(Long userId) {
        return watchlistItemRepository.findByIdUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<WatchlistItemResponseDTO> addToWatchlist(Long userId, AddWatchlistItemRequestDTO request) {
        Long stockId = parseStockId(request.getStockId());

        WatchlistItem watchlistItem = watchlistItemRepository.findByIdUserIdAndIdStockId(userId, stockId)
                .orElseGet(() -> newWatchlistItem(userId, stockId));

        watchlistItem.setQuantity(BigDecimal.valueOf(request.getQuantity()));
        watchlistItemRepository.save(watchlistItem);
        return getWatchlist(userId);
    }

    @Transactional
    public List<WatchlistItemResponseDTO> updateWatchlistItem(Long userId, String stockId, Long quantity) {
        Long parsedStockId = parseStockId(stockId);
        watchlistItemRepository.findByIdUserIdAndIdStockId(userId, parsedStockId)
                .ifPresent(item -> {
                    item.setQuantity(BigDecimal.valueOf(quantity));
                    watchlistItemRepository.save(item);
                });
        return getWatchlist(userId);
    }

    @Transactional
    public List<WatchlistItemResponseDTO> removeFromWatchlist(Long userId, String stockId) {
        watchlistItemRepository.deleteByIdUserIdAndIdStockId(userId, parseStockId(stockId));
        return getWatchlist(userId);
    }

    @Transactional
    public List<WatchlistItemResponseDTO> clearWatchlist(Long userId) {
        watchlistItemRepository.deleteByIdUserId(userId);
        return List.of();
    }

    private WatchlistItemResponseDTO toResponse(WatchlistItem watchlistItem) {
        WatchlistItemResponseDTO response = new WatchlistItemResponseDTO();
        response.setStockId(String.valueOf(watchlistItem.getId().getStockId()));
        response.setQuantity(watchlistItem.getQuantity() != null ? watchlistItem.getQuantity().longValue() : 0L);
        return response;
    }

    private WatchlistItem newWatchlistItem(Long userId, Long stockId) {
        WatchlistItem item = new WatchlistItem();
        WatchlistItemId id = new WatchlistItemId();
        id.setUserId(userId);
        id.setStockId(stockId);
        item.setId(id);
        item.setQuantity(BigDecimal.ONE);
        return item;
    }

    private Long parseStockId(String stockId) {
        try {
            return Long.parseLong(stockId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid stockId format: " + stockId);
        }
    }

}
