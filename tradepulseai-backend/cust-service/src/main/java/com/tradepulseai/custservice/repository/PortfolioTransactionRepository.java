package com.tradepulseai.custservice.repository;

import com.tradepulseai.custservice.model.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, UUID> {

    List<PortfolioTransaction> findByUserEmailOrderByExecutedAtDesc(String userEmail);
}

