package com.tradepulseai.stockservice.repository;

import com.tradepulseai.stockservice.model.FeaturedStockCache;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeaturedStockCacheRepository extends JpaRepository<FeaturedStockCache, Long> {

    /**
     * Get all featured stocks ordered by rank (sort_order)
     * EntityGraph ensures stock is eagerly loaded (avoids N+1 query)
     */
    @EntityGraph(attributePaths = "stock")
    List<FeaturedStockCache> findAllByOrderBySortOrderAsc();
}
