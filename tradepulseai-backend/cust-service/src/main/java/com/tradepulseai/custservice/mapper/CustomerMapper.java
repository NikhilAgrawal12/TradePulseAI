package com.tradepulseai.custservice.mapper;

import com.tradepulseai.custservice.dto.CustomerRequestDTO;
import com.tradepulseai.custservice.dto.CustomerResponseDTO;
import com.tradepulseai.custservice.model.Customer;

import java.time.LocalDate;

public class CustomerMapper {

    public static CustomerResponseDTO toDTO(Customer cust){
        CustomerResponseDTO custDTO = new CustomerResponseDTO();
        custDTO.setCustomerId(cust.getCustomerId().toString());
        custDTO.setFirstName(cust.getFirstName());
        custDTO.setLastName(cust.getLastName());
        custDTO.setEmail(cust.getEmail());
        custDTO.setPhoneNumber(cust.getPhoneNumber());
        custDTO.setAddress(cust.getAddress());
        custDTO.setCity(cust.getCity());
        custDTO.setState(cust.getState());
        custDTO.setCountry(cust.getCountry());
        custDTO.setDateOfBirth(cust.getDateOfBirth().toString());
        return custDTO;
    }

    public static Customer toModel(CustomerRequestDTO custRequestDTO){
        Customer cust = new Customer();
        cust.setFirstName(custRequestDTO.getFirstName());
        cust.setLastName(custRequestDTO.getLastName());
        cust.setEmail(custRequestDTO.getEmail());
        cust.setPhoneNumber(custRequestDTO.getPhoneNumber());
        cust.setAddress(custRequestDTO.getAddress());
        cust.setCity(custRequestDTO.getCity());
        cust.setState(custRequestDTO.getState());
        cust.setCountry(custRequestDTO.getCountry());
        cust.setDateOfBirth(LocalDate.parse(custRequestDTO.getDateOfBirth()));
        cust.setRegistrationDate(LocalDate.parse(custRequestDTO.getRegisteredDate()));
        return cust;
    }
}
