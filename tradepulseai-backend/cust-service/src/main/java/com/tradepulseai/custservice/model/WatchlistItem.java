package com.tradepulseai.custservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "watchlist_items")
public class WatchlistItem {

    @EmbeddedId
    private WatchlistItemId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    public WatchlistItemId getId() {
        return id;
    }

    public void setId(WatchlistItemId id) {
        this.id = id;
    }


    public Instant getCreatedAt() {
        return createdAt;
    }
}
