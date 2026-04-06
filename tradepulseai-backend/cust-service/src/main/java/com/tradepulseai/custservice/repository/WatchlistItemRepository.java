package com.tradepulseai.custservice.repository;

import com.tradepulseai.custservice.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {

    List<WatchlistItem> findByUserEmailOrderByUpdatedAtDesc(String userEmail);

    Optional<WatchlistItem> findByUserEmailAndStockId(String userEmail, String stockId);

    void deleteByUserEmailAndStockId(String userEmail, String stockId);

    void deleteByUserEmail(String userEmail);
}

