package com.tradepulse.customerservice.repository;

import com.tradepulse.customerservice.model.WatchlistItem;
import com.tradepulse.customerservice.model.WatchlistItemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, WatchlistItemId> {

    List<WatchlistItem> findByIdUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WatchlistItem> findByIdUserIdAndIdStockId(Long userId, Long stockId);

    void deleteByIdUserIdAndIdStockId(Long userId, Long stockId);

    void deleteByIdUserId(Long userId);
}
