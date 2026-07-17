package com.tradepulse.stockservice.repository;

import com.tradepulse.stockservice.model.AllStocksLastValueCache;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AllStocksLastValueCacheRepository extends JpaRepository<AllStocksLastValueCache, Long> {

    /**
     * Find cache entry by stock ID with eager loading
     */
    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    Optional<AllStocksLastValueCache> findByStockStockId(Long stockId);

    /**
     * Find cache entry by stock symbol with eager loading
     */
    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    Optional<AllStocksLastValueCache> findByStockSymbolIgnoreCase(String symbol);

    /**
     * Get all cache entries with eager loading (avoids N+1 query)
     */
    @EntityGraph(attributePaths = {"stock", "stock.exchange"})
    List<AllStocksLastValueCache> findAll();
}

