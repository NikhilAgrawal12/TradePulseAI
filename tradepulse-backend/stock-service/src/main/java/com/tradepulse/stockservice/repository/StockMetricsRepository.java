package com.tradepulse.stockservice.repository;

import com.tradepulse.stockservice.model.StockMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMetricsRepository extends JpaRepository<StockMetrics, Long> {
}
