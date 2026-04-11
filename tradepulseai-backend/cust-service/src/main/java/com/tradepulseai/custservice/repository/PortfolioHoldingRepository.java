package com.tradepulseai.custservice.repository;

import com.tradepulseai.custservice.model.PortfolioHolding;
import com.tradepulseai.custservice.model.PortfolioHoldingId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, PortfolioHoldingId> {

    List<PortfolioHolding> findByIdUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<PortfolioHolding> findByIdUserIdAndIdStockId(Long userId, Long stockId);
}
