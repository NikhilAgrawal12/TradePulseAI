package com.tradepulseai.stockservice.repository;

import com.tradepulseai.stockservice.model.Stock;
import com.tradepulseai.stockservice.model.StockMarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockMarketDataRepository extends JpaRepository<StockMarketData, Long> {

    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    Optional<StockMarketData> findTopByStockOrderByTradingDateDesc(Stock stock);

    boolean existsByStockAndTradingDate(Stock stock, LocalDate tradingDate);

    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    Optional<StockMarketData> findTopByOrderByTradingDateDesc();

    @Query("SELECT MAX(smd.tradingDate) FROM StockMarketData smd")
    Optional<LocalDate> findMaxTradingDate();

    @Query("SELECT smd.stock.stockId FROM StockMarketData smd WHERE smd.tradingDate = :tradingDate")
    List<Long> findStockIdsByTradingDate(@Param("tradingDate") LocalDate tradingDate);

    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    @Query("""
            SELECT smd
            FROM StockMarketData smd
            WHERE smd.stock.stockId = :stockId
            ORDER BY smd.tradingDate DESC
            """)
    List<StockMarketData> findRecentByStockId(@Param("stockId") Long stockId, Pageable pageable);

    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
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

    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    Optional<StockMarketData> findByStockAndTradingDate(Stock stock, LocalDate tradingDate);

    @EntityGraph(attributePaths = {"stock"})
    List<StockMarketData> findAllByTradingDate(LocalDate tradingDate);

    @EntityGraph(attributePaths = {"stock"})
    List<StockMarketData> findByDailyNewsIsNotNullOrderByTradingDateDesc(Pageable pageable);
}
