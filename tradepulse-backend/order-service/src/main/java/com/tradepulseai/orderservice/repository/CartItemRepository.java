package com.tradepulseai.orderservice.repository;

import com.tradepulseai.orderservice.model.CartItem;
import com.tradepulseai.orderservice.model.CartItemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {

    List<CartItem> findByIdUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<CartItem> findByIdUserIdAndIdStockId(Long userId, Long stockId);

    void deleteByIdUserIdAndIdStockId(Long userId, Long stockId);

    void deleteByIdUserId(Long userId);
}

