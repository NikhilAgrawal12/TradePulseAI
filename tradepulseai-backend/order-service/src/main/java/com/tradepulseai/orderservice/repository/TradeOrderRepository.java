package com.tradepulseai.orderservice.repository;

import com.tradepulseai.orderservice.model.TradeOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, UUID> {

    @EntityGraph(attributePaths = "items")
    List<TradeOrder> findByUserEmailOrderByCreatedAtDesc(String userEmail);
}

