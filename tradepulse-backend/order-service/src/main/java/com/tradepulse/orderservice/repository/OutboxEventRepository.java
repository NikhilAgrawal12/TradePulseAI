package com.tradepulse.orderservice.repository;

import com.tradepulse.orderservice.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * Claim up to 50 PENDING rows atomically using SKIP LOCKED so concurrent relay instances
     * don't pick up the same events.
     */
    @Modifying
    @Query(value = """
            UPDATE outbox_events
               SET status = 'IN_PROGRESS'
             WHERE outbox_id IN (
                   SELECT outbox_id FROM outbox_events
                    WHERE status = 'PENDING'
                    ORDER BY created_at
                    LIMIT 50
                    FOR UPDATE SKIP LOCKED
             )
            """, nativeQuery = true)
    int claimPendingBatch();

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = :status, o.publishedAt = :now WHERE o.id = :id")
    void markAs(@Param("id") String id, @Param("status") String status, @Param("now") Instant now);
}


