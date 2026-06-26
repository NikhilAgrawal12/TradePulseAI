package com.tradepulseai.custservice.repository;

import com.tradepulseai.custservice.model.PortfolioHolding;
import com.tradepulseai.custservice.model.PortfolioHoldingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, PortfolioHoldingId> {

    @EntityGraph(attributePaths = "id")
    List<PortfolioHolding> findByIdUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "id")
    Optional<PortfolioHolding> findByIdUserIdAndIdStockId(Long userId, Long stockId);
}
