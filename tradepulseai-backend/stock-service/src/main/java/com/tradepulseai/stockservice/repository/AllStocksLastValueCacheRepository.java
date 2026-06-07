package com.tradepulseai.stockservice.repository;

import com.tradepulseai.stockservice.model.AllStocksLastValueCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AllStocksLastValueCacheRepository extends JpaRepository<AllStocksLastValueCache, Long> {

    /**
     * Find cache entry by stock ID
     */
    Optional<AllStocksLastValueCache> findByStockStockId(Long stockId);

    /**
     * Find cache entry by stock symbol (case-insensitive)
     */
    Optional<AllStocksLastValueCache> findByStockSymbolIgnoreCase(String symbol);
}

