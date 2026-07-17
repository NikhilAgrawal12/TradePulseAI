package com.tradepulseai.stockservice.repository;

import com.tradepulseai.stockservice.model.StockMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMetricsRepository extends JpaRepository<StockMetrics, Long> {
}
