package com.tradepulseai.custservice.service;

import com.tradepulseai.custservice.dto.CustomerRequestDTO;
import com.tradepulseai.custservice.dto.CustomerResponseDTO;
import com.tradepulseai.custservice.mapper.CustomerMapper;
import com.tradepulseai.custservice.model.Customer;
import com.tradepulseai.custservice.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<CustomerResponseDTO> getCustomers() {
        List<Customer> users = customerRepository.findAll();

        return users.stream().map(CustomerMapper::toDTO).toList();
    }

    public CustomerResponseDTO createCustomer(CustomerRequestDTO customerRequestDTO) {

        if (customerRepository.existsByEmail(customerRequestDTO.getEmail())) {
            throw new EmailAlreadyExistsException("A user with this email already exists " + customerRequestDTO.getEmail());
        }

        Customer customer = customerRepository.save(CustomerMapper.toModel(customerRequestDTO));
        return CustomerMapper.toDTO(customer);
    }
}
