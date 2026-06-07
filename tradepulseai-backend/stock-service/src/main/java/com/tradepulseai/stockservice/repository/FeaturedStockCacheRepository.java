package com.tradepulseai.stockservice.repository;

import com.tradepulseai.stockservice.model.FeaturedStockCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeaturedStockCacheRepository extends JpaRepository<FeaturedStockCache, Long> {

    /**
     * Get all featured stocks ordered by rank (sort_order)
     */
    List<FeaturedStockCache> findAllByOrderBySortOrderAsc();

    /**
     * Check if a stock is in the featured cache
     */
    Optional<FeaturedStockCache> findByStockStockId(Long stockId);

    /**
     * Delete all cached entries (for refreshing the cache)
     */
    void deleteAll();
}

