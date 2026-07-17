package com.tradepulseai.portfolioservice.repository;

import com.tradepulseai.portfolioservice.model.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {
    List<PortfolioTransaction> findByUserIdOrderByExecutedAtDesc(Long userId);
}

