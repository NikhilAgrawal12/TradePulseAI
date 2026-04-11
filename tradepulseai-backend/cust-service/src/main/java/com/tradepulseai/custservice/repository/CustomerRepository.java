package com.tradepulseai.custservice.repository;

import com.tradepulseai.custservice.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    boolean existsByUserId(Long userId);
    Optional<Customer> findByUserId(Long userId);
}
