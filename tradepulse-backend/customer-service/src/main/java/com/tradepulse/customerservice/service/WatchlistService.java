package com.tradepulse.customerservice.service;

import com.tradepulse.customerservice.dto.watchlist.AddWatchlistItemRequestDTO;
import com.tradepulse.customerservice.dto.watchlist.WatchlistItemResponseDTO;
import com.tradepulse.customerservice.model.WatchlistItem;
import com.tradepulse.customerservice.model.WatchlistItemId;
import com.tradepulse.customerservice.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        watchlistItemRepository.save(watchlistItem);
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
        return response;
    }

    private WatchlistItem newWatchlistItem(Long userId, Long stockId) {
        WatchlistItem item = new WatchlistItem();
        WatchlistItemId id = new WatchlistItemId();
        id.setUserId(userId);
        id.setStockId(stockId);
        item.setId(id);
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
