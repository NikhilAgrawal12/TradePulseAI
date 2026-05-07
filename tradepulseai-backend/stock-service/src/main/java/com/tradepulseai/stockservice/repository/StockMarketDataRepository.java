package com.tradepulseai.stockservice.repository;

import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockMarketDataRepository extends JpaRepository<StockMarketData, Long> {

    Optional<StockMarketData> findTopByStockOrderByMarketTimestampDesc(Stock stock);

    boolean existsByStockAndMarketTimestamp(Stock stock, Instant marketTimestamp);

    boolean existsBySource(String source);

    boolean existsByMarketTimestamp(Instant marketTimestamp);

    Optional<StockMarketData> findTopByOrderByMarketTimestampDesc();

    @Query("""
            SELECT smd
            FROM StockMarketData smd
            WHERE smd.marketTimestamp = (
                SELECT MAX(s2.marketTimestamp)
                FROM StockMarketData s2
                WHERE s2.stock = smd.stock
            )
            """)
    List<StockMarketData> findLatestForAllStocks();
}

