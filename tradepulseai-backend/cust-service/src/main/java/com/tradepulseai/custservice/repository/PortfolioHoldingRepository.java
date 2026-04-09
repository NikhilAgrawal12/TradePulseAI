package com.tradepulseai.custservice.repository;

import com.tradepulseai.custservice.model.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, UUID> {

    List<PortfolioHolding> findByUserEmailOrderByUpdatedAtDesc(String userEmail);

    Optional<PortfolioHolding> findByUserEmailAndStockId(String userEmail, String stockId);
}

