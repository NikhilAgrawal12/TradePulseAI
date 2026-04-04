package com.tradepulseai.custservice.service;

import com.tradepulseai.custservice.dto.CustomerRequestDTO;
import com.tradepulseai.custservice.dto.CustomerResponseDTO;
import com.tradepulseai.custservice.exception.CustomerNotFoundException;
import com.tradepulseai.custservice.exception.EmailAlreadyExistsException;
import com.tradepulseai.custservice.grpc.PaymentServiceGrpcClient;
import com.tradepulseai.custservice.kafka.kafkaProducer;
import com.tradepulseai.custservice.mapper.CustomerMapper;
import com.tradepulseai.custservice.model.Customer;
import com.tradepulseai.custservice.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final PaymentServiceGrpcClient paymentServiceGrpcClient;
    private final kafkaProducer kafkaProducer;

    public CustomerService(CustomerRepository customerRepository, PaymentServiceGrpcClient paymentServiceGrpcClient, kafkaProducer kafkaProducer) {

        this.customerRepository = customerRepository;
        this.paymentServiceGrpcClient = paymentServiceGrpcClient;
        this.kafkaProducer = kafkaProducer;
    }

    public List<CustomerResponseDTO> getCustomers() {
        List<Customer> customers = customerRepository.findAll();

        return customers.stream().map(CustomerMapper::toDTO).toList();
    }

    public CustomerResponseDTO getCustomerByEmail(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with email: " + email));
        return CustomerMapper.toDTO(customer);
    }

    public CustomerResponseDTO createCustomer(CustomerRequestDTO customerRequestDTO) {

        if (customerRepository.existsByEmail(customerRequestDTO.getEmail())) {
            throw new EmailAlreadyExistsException("A customer with this email already exists " + customerRequestDTO.getEmail());
        }

        Customer customer = customerRepository.save(CustomerMapper.toModel(customerRequestDTO));

            paymentServiceGrpcClient.createPaymentAccount(customer.getCustomerId().toString(), customer.getFirstName(), customer.getEmail());

            kafkaProducer.sendEvent(customer);
        return CustomerMapper.toDTO(customer);
    }

    public CustomerResponseDTO updateCustomer(UUID id, CustomerRequestDTO customerRequestDTO) {

        Customer customer = customerRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException("Patient not found with ID: " + id));

        if (customerRepository.existsByEmailAndCustomerIdNot(customerRequestDTO.getEmail(), id)) {
            throw new EmailAlreadyExistsException("A customer with this email already exists " + customerRequestDTO.getEmail());
        }

        customer.setFirstName(customerRequestDTO.getFirstName());
        customer.setLastName(customerRequestDTO.getLastName());
        customer.setEmail(customerRequestDTO.getEmail());
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


    public void deleteCustomer(UUID id) {
        customerRepository.deleteById(id);
    }
}
