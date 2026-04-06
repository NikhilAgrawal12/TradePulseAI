package com.tradepulseai.custservice.repository;

import com.tradepulseai.custservice.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailAndCustomerIdNot(String email, UUID customerId);
    boolean existsByEmailIgnoreCaseAndCustomerIdNot(String email, UUID customerId);
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByEmailIgnoreCase(String email);
}
