package com.tradepulseai.custservice.service;

import com.tradepulseai.custservice.client.AuthServiceClient;
import com.tradepulseai.custservice.dto.customer.CustomerRequestDTO;
import com.tradepulseai.custservice.dto.customer.CustomerResponseDTO;
import com.tradepulseai.custservice.exception.CustomerNotFoundException;
import com.tradepulseai.custservice.exception.EmailAlreadyExistsException;
import com.tradepulseai.custservice.kafka.kafkaProducer;
import com.tradepulseai.custservice.mapper.CustomerMapper;
import com.tradepulseai.custservice.model.Customer;
import com.tradepulseai.custservice.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final AuthServiceClient authServiceClient;
    private final kafkaProducer kafkaProducer;

    public CustomerService(CustomerRepository customerRepository, AuthServiceClient authServiceClient, kafkaProducer kafkaProducer) {

        this.customerRepository = customerRepository;
        this.authServiceClient = authServiceClient;
        this.kafkaProducer = kafkaProducer;
    }

    public CustomerResponseDTO getCustomerByUserId(Long userId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with userId: " + userId));

        String resolvedEmail = normalizeEmail(authServiceClient.getUserById(userId).email());
        return CustomerMapper.toDTO(customer, resolvedEmail);
    }

    public CustomerResponseDTO createCustomer(CustomerRequestDTO customerRequestDTO) {
        if (customerRequestDTO.getUserId() == null) {
            throw new IllegalArgumentException("User id is required");
        }

        AuthServiceClient.AuthUser authUser = authServiceClient.getUserById(customerRequestDTO.getUserId());
        String normalizedEmail = normalizeEmail(authUser.email());

        if (customerRepository.existsByUserId(authUser.userId())) {
            throw new EmailAlreadyExistsException("A customer with this user already exists " + normalizedEmail);
        }
        customerRequestDTO.setUserId(authUser.userId());

        Customer customer = customerRepository.save(CustomerMapper.toModel(customerRequestDTO));


        kafkaProducer.sendEvent(customer, normalizedEmail);
        return CustomerMapper.toDTO(customer, normalizedEmail);
    }

    public CustomerResponseDTO updateCustomer(Long id, CustomerRequestDTO customerRequestDTO) {
        Customer customer = customerRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException("Patient not found with ID: " + id));

        if (customerRequestDTO.getUserId() != null && !Objects.equals(customerRequestDTO.getUserId(), customer.getUserId())) {
            throw new IllegalArgumentException("userId cannot be changed for an existing customer");
        }

        customer.setFirstName(customerRequestDTO.getFirstName());
        customer.setLastName(customerRequestDTO.getLastName());
        customer.setAddressLine1(customerRequestDTO.getAddressLine1());
        customer.setAddressLine2(customerRequestDTO.getAddressLine2());
        customer.setCity(customerRequestDTO.getCity());
        customer.setState(customerRequestDTO.getState());
        customer.setPostalCode(customerRequestDTO.getPostalCode());
        customer.setCountry(customerRequestDTO.getCountry());
        customer.setDateOfBirth(LocalDate.parse(customerRequestDTO.getDateOfBirth()));
        customer.setPhoneNumber(customerRequestDTO.getPhoneNumber());

        Customer updatedcustomer = customerRepository.save(customer);
        String resolvedEmail = normalizeEmail(authServiceClient.getUserById(updatedcustomer.getUserId()).email());
        return CustomerMapper.toDTO(updatedcustomer, resolvedEmail);

    }


    public void deleteCustomer(Long id) {
        customerRepository.deleteById(id);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }

        return email.trim().toLowerCase();
    }
}
