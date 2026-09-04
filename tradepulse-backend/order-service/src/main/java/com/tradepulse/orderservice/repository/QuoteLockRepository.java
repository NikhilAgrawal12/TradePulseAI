package com.tradepulse.orderservice.repository;

import com.tradepulse.orderservice.model.QuoteLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface QuoteLockRepository extends JpaRepository<QuoteLock, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "items")
    Optional<QuoteLock> findByIdAndUserId(String id, Long userId);
}


