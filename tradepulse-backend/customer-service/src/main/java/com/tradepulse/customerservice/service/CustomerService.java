package com.tradepulse.customerservice.service;

import com.tradepulse.customerservice.client.AuthServiceClient;
import com.tradepulse.customerservice.dto.customer.CustomerRegistrationRequestDTO;
import com.tradepulse.customerservice.dto.customer.CustomerRequestDTO;
import com.tradepulse.customerservice.dto.customer.CustomerResponseDTO;
import com.tradepulse.customerservice.exception.CustomerNotFoundException;
import com.tradepulse.customerservice.exception.EmailAlreadyExistsException;
import com.tradepulse.customerservice.kafka.kafkaProducer;
import com.tradepulse.customerservice.mapper.CustomerMapper;
import com.tradepulse.customerservice.model.Customer;
import com.tradepulse.customerservice.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class CustomerService {
    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

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

        return CustomerMapper.toDTO(customer);
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

    @Transactional
    public CustomerResponseDTO registerCustomer(CustomerRegistrationRequestDTO requestDTO) {
        AuthServiceClient.AuthUser createdAuthUser = null;

        try {
            createdAuthUser = authServiceClient.registerUser(requestDTO);
            String normalizedEmail = normalizeEmail(createdAuthUser.email());

            if (customerRepository.existsByUserId(createdAuthUser.userId())) {
                throw new EmailAlreadyExistsException("A customer with this user already exists " + normalizedEmail);
            }

            CustomerRequestDTO customerRequestDTO = mapToCustomerRequest(requestDTO, createdAuthUser.userId(), normalizedEmail);
            Customer customer = customerRepository.save(CustomerMapper.toModel(customerRequestDTO));
            kafkaProducer.sendEventOrThrow(customer, normalizedEmail);
            return CustomerMapper.toDTO(customer, normalizedEmail);
        } catch (Exception exception) {
            if (createdAuthUser != null && createdAuthUser.userId() != null) {
                try {
                    rollbackAuthUser(createdAuthUser.userId());
                } catch (Exception rollbackException) {
                    log.error("Customer registration compensation failed for userId={}", createdAuthUser.userId(), rollbackException);
                    throw new IllegalStateException(
                            "Customer registration failed and compensation rollback failed for userId=" + createdAuthUser.userId(),
                            rollbackException
                    );
                }
            }
            throw exception;
        }
    }

    public CustomerResponseDTO updateCustomer(Long userId, CustomerRequestDTO customerRequestDTO) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with userId: " + userId));

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
        return CustomerMapper.toDTO(updatedcustomer);

    }


    public void deleteCustomer(Long userId) {
        customerRepository.deleteByUserId(userId);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }

        return email.trim().toLowerCase();
    }

    private CustomerRequestDTO mapToCustomerRequest(CustomerRegistrationRequestDTO source, Long userId, String email) {
        CustomerRequestDTO target = new CustomerRequestDTO();
        target.setUserId(userId);
        target.setEmail(email);
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setPhoneNumber(source.getPhoneNumber());
        target.setAddressLine1(source.getAddressLine1());
        target.setAddressLine2(source.getAddressLine2());
        target.setCity(source.getCity());
        target.setState(source.getState());
        target.setPostalCode(source.getPostalCode());
        target.setCountry(source.getCountry());
        target.setDateOfBirth(source.getDateOfBirth());
        target.setRegistrationDate(source.getRegistrationDate());
        return target;
    }

    private void rollbackAuthUser(Long userId) {
        authServiceClient.deleteUserById(userId);
    }
}
