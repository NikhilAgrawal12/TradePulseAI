package com.tradepulseai.paymentservice.repository;

import com.tradepulseai.paymentservice.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByUserEmail(String userEmail);
    List<Payment> findByStockId(String stockId);
}

