package com.tradepulseai.custservice.service;

import com.tradepulseai.custservice.dto.AddWatchlistItemRequestDTO;
import com.tradepulseai.custservice.dto.UpdateWatchlistItemRequestDTO;
import com.tradepulseai.custservice.dto.WatchlistItemResponseDTO;
import com.tradepulseai.custservice.model.WatchlistItem;
import com.tradepulseai.custservice.repository.WatchlistItemRepository;
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
    public List<WatchlistItemResponseDTO> getWatchlist(String userEmail) {
        return watchlistItemRepository.findByUserEmailOrderByUpdatedAtDesc(userEmail)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<WatchlistItemResponseDTO> addToWatchlist(String userEmail, AddWatchlistItemRequestDTO request) {
        WatchlistItem watchlistItem = watchlistItemRepository.findByUserEmailAndStockId(userEmail, request.getStockId())
                .orElseGet(WatchlistItem::new);

        if (watchlistItem.getId() == null) {
            watchlistItem.setUserEmail(userEmail);
            watchlistItem.setStockId(request.getStockId());
            watchlistItem.setSymbol(request.getSymbol());
            watchlistItem.setRefPrice(request.getRefPrice());
            watchlistItem.setQuantity(request.getQuantity());
        } else {
            watchlistItem.setSymbol(request.getSymbol());
            watchlistItem.setRefPrice(request.getRefPrice());
            watchlistItem.setQuantity(watchlistItem.getQuantity() + request.getQuantity());
        }

        watchlistItemRepository.save(watchlistItem);
        return getWatchlist(userEmail);
    }

    @Transactional
    public List<WatchlistItemResponseDTO> updateWatchlistItem(String userEmail, String stockId, UpdateWatchlistItemRequestDTO request) {
        WatchlistItem watchlistItem = watchlistItemRepository.findByUserEmailAndStockId(userEmail, stockId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist item not found for stockId: " + stockId));

        watchlistItem.setRefPrice(request.getRefPrice());
        watchlistItem.setQuantity(request.getQuantity());
        watchlistItemRepository.save(watchlistItem);
        return getWatchlist(userEmail);
    }

    @Transactional
    public List<WatchlistItemResponseDTO> removeFromWatchlist(String userEmail, String stockId) {
        watchlistItemRepository.deleteByUserEmailAndStockId(userEmail, stockId);
        return getWatchlist(userEmail);
    }

    @Transactional
    public List<WatchlistItemResponseDTO> clearWatchlist(String userEmail) {
        watchlistItemRepository.deleteByUserEmail(userEmail);
        return List.of();
    }

    private WatchlistItemResponseDTO toResponse(WatchlistItem watchlistItem) {
        WatchlistItemResponseDTO response = new WatchlistItemResponseDTO();
        response.setId(watchlistItem.getId());
        response.setStockId(watchlistItem.getStockId());
        response.setSymbol(watchlistItem.getSymbol());
        response.setRefPrice(watchlistItem.getRefPrice());
        response.setQuantity(watchlistItem.getQuantity());
        return response;
    }
}

