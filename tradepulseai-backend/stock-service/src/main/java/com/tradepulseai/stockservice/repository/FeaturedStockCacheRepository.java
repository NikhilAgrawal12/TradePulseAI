package com.tradepulseai.stockservice.repository;

import com.tradepulseai.stockservice.model.FeaturedStockCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeaturedStockCacheRepository extends JpaRepository<FeaturedStockCache, Long> {

    /**
     * Get all featured stocks ordered by rank (sort_order)
     */
    List<FeaturedStockCache> findAllByOrderBySortOrderAsc();
}
