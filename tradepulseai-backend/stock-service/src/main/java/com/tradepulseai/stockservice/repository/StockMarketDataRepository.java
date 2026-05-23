package com.tradepulseai.stockservice.repository;

import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockMarketDataRepository extends JpaRepository<StockMarketData, Long> {

    Optional<StockMarketData> findTopByStockOrderByTradingDateDesc(Stock stock);

    boolean existsByStockAndTradingDate(Stock stock, LocalDate tradingDate);

    Optional<StockMarketData> findTopByOrderByTradingDateDesc();

    @Query("""
            SELECT smd
            FROM StockMarketData smd
            WHERE smd.tradingDate = (
                SELECT MAX(s2.tradingDate)
                FROM StockMarketData s2
                WHERE s2.stock = smd.stock
            )
            """)
    List<StockMarketData> findLatestForAllStocks();
}

