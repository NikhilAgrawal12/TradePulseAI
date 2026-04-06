package com.tradepulseai.orderservice.repository;

import com.tradepulseai.orderservice.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByUserEmailOrderByUpdatedAtDesc(String userEmail);

    Optional<CartItem> findByUserEmailAndStockId(String userEmail, String stockId);

    void deleteByUserEmailAndStockId(String userEmail, String stockId);

    void deleteByUserEmail(String userEmail);
}

