package com.tradepulseai.stockservice.repository;

import com.tradepulseai.stockservice.model.Stock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findBySymbol(String symbol);

    List<Stock> findAllByOrderByStockIdAsc();

    @Query("""
            SELECT s
            FROM Stock s
            ORDER BY COALESCE(s.marketCap, 0) DESC, s.stockId ASC
            """)
    List<Stock> findTopByMarketCap(Pageable pageable);

    List<Stock> findByFeaturedTrueOrderBySortOrderAsc();
}
