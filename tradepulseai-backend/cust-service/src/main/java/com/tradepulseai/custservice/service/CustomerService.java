package com.tradepulseai.custservice.service;

import com.tradepulseai.custservice.client.AuthServiceClient;
import com.tradepulseai.custservice.dto.CustomerRequestDTO;
import com.tradepulseai.custservice.dto.CustomerResponseDTO;
import com.tradepulseai.custservice.exception.CustomerNotFoundException;
import com.tradepulseai.custservice.exception.EmailAlreadyExistsException;
import com.tradepulseai.custservice.grpc.PaymentServiceGrpcClient;
import com.tradepulseai.custservice.kafka.kafkaProducer;
import com.tradepulseai.custservice.mapper.CustomerMapper;
import com.tradepulseai.custservice.model.Customer;
import com.tradepulseai.custservice.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class CustomerService {
    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private final CustomerRepository customerRepository;
    private final AuthServiceClient authServiceClient;
    private final PaymentServiceGrpcClient paymentServiceGrpcClient;
    private final kafkaProducer kafkaProducer;

    public CustomerService(CustomerRepository customerRepository, AuthServiceClient authServiceClient, PaymentServiceGrpcClient paymentServiceGrpcClient, kafkaProducer kafkaProducer) {

        this.customerRepository = customerRepository;
        this.authServiceClient = authServiceClient;
        this.paymentServiceGrpcClient = paymentServiceGrpcClient;
        this.kafkaProducer = kafkaProducer;
    }

    public List<CustomerResponseDTO> getCustomers() {
        List<Customer> customers = customerRepository.findAll();

        return customers.stream()
                .map(customer -> {
                    AuthServiceClient.AuthUser authUser = authServiceClient.getUserById(customer.getUserId());
                    return CustomerMapper.toDTO(customer, authUser.email());
                })
                .toList();
    }

    public CustomerResponseDTO getCustomerByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        AuthServiceClient.AuthUser authUser = authServiceClient.getUserByEmail(normalizedEmail);
        Customer customer = customerRepository.findByUserId(authUser.userId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with email: " + normalizedEmail));
        return CustomerMapper.toDTO(customer, authUser.email());
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

        try {
            paymentServiceGrpcClient.createPaymentAccount(customer.getCustomerId().toString(), customer.getFirstName(), normalizedEmail);
        } catch (Exception ex) {
            // Keep signup flow available even if payment-service gRPC is temporarily down.
            log.warn("Payment account creation skipped for customer {}: {}", customer.getCustomerId(), ex.getMessage());
        }

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
