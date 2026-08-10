package com.tradepulse.portfolioservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Records every ORDER_COMPLETED event that has been successfully applied
 * so duplicate deliveries are ignored (at-least-once → effectively-once).
 */
@Entity
@Table(
        name = "processed_events",
        uniqueConstraints = @UniqueConstraint(name = "uq_processed_event_id", columnNames = "event_id")
)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 64, nullable = false, updatable = false)
    private String eventId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @PrePersist
    public void prePersist() {
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Instant getProcessedAt() { return processedAt; }
}

