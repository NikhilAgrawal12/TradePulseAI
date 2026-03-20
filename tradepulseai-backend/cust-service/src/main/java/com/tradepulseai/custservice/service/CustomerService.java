package com.tradepulseai.custservice.service;

import com.tradepulseai.custservice.dto.CustomerRequestDTO;
import com.tradepulseai.custservice.dto.CustomerResponseDTO;
import com.tradepulseai.custservice.exception.CustomerNotFoundException;
import com.tradepulseai.custservice.exception.EmailAlreadyExistsException;
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
    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<CustomerResponseDTO> getCustomers() {
        List<Customer> customers = customerRepository.findAll();

        return customers.stream().map(CustomerMapper::toDTO).toList();
    }

    public CustomerResponseDTO createCustomer(CustomerRequestDTO customerRequestDTO) {

        if (customerRepository.existsByEmail(customerRequestDTO.getEmail())) {
            throw new EmailAlreadyExistsException("A customer with this email already exists " + customerRequestDTO.getEmail());
        }

        Customer customer = customerRepository.save(CustomerMapper.toModel(customerRequestDTO));
        return CustomerMapper.toDTO(customer);
    }

    public CustomerResponseDTO updateCustomer(UUID id, CustomerRequestDTO customerRequestDTO) {

        Customer customer = customerRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException("Patient not found with ID: " + id));

        if (customerRepository.existsByEmailAndCustomerIdNot(customerRequestDTO.getEmail(),id)) {
            throw new EmailAlreadyExistsException("A customer with this email already exists " + customerRequestDTO.getEmail());
        }

        customer.setFirstName(customerRequestDTO.getFirstName());
        customer.setLastName(customerRequestDTO.getLastName());
        customer.setEmail(customerRequestDTO.getEmail());
        customer.setAddress(customerRequestDTO.getAddress());
        customer.setCity(customerRequestDTO.getCity());
        customer.setState(customerRequestDTO.getState());
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
