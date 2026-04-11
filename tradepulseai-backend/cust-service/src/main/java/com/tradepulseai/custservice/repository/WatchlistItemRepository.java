package com.tradepulseai.custservice.repository;

import com.tradepulseai.custservice.model.WatchlistItem;
import com.tradepulseai.custservice.model.WatchlistItemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, WatchlistItemId> {

    List<WatchlistItem> findByIdUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<WatchlistItem> findByIdUserIdAndIdStockId(Long userId, Long stockId);

    void deleteByIdUserIdAndIdStockId(Long userId, Long stockId);

    void deleteByIdUserId(Long userId);
}
