package com.tradepulseai.orderservice.repository;

import com.tradepulseai.orderservice.model.TradeOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    @EntityGraph(attributePaths = "items")
    List<TradeOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
}

