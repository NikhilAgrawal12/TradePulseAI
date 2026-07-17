package com.tradepulse.orderservice.repository;

import com.tradepulse.orderservice.model.TradeOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, String> {

    @EntityGraph(attributePaths = "items")
    List<TradeOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "items")
    Page<TradeOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    boolean existsByOrderNumber(Integer orderNumber);
}

