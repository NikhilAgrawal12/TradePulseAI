package com.tradepulseai.stockservice.repository;

import com.tradepulseai.stockservice.model.Stock;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    @Cacheable(value = "stock_by_symbol", key = "#symbol")
    Optional<Stock> findBySymbol(String symbol);

    List<Stock> findAllByOrderByStockIdAsc();

    @Cacheable(value = "featured_stocks", unless = "#result == null || #result.isEmpty()")
    List<Stock> findByFeaturedTrueOrderBySortOrderAsc();
}
