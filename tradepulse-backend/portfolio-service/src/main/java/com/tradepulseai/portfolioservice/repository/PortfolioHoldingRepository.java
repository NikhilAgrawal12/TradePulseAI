package com.tradepulseai.portfolioservice.repository;

import com.tradepulseai.portfolioservice.model.PortfolioHolding;
import com.tradepulseai.portfolioservice.model.PortfolioHoldingId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, PortfolioHoldingId> {

    @EntityGraph(attributePaths = "id")
    List<PortfolioHolding> findByIdUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "id")
    Optional<PortfolioHolding> findByIdUserIdAndIdStockId(Long userId, Long stockId);
}

