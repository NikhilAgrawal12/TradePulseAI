package com.tradepulseai.custservice.mapper;

import com.tradepulseai.custservice.dto.CustomerRequestDTO;
import com.tradepulseai.custservice.dto.CustomerResponseDTO;
import com.tradepulseai.custservice.model.Customer;

import java.time.Instant;
import java.time.LocalDate;

public class CustomerMapper {

    public static CustomerResponseDTO toDTO(Customer cust, String email){
        CustomerResponseDTO custDTO = new CustomerResponseDTO();
        custDTO.setCustomerId(cust.getCustomerId());
        custDTO.setUserId(cust.getUserId());
        custDTO.setFirstName(cust.getFirstName());
        custDTO.setLastName(cust.getLastName());
        custDTO.setEmail(email);
        custDTO.setPhoneNumber(cust.getPhoneNumber());
        custDTO.setAddressLine1(cust.getAddressLine1());
        custDTO.setAddressLine2(cust.getAddressLine2());
        custDTO.setCity(cust.getCity());
        custDTO.setState(cust.getState());
        custDTO.setPostalCode(cust.getPostalCode());
        custDTO.setCountry(cust.getCountry());
        custDTO.setDateOfBirth(cust.getDateOfBirth().toString());
        return custDTO;
    }

    public static Customer toModel(CustomerRequestDTO custRequestDTO){
        Customer cust = new Customer();
        cust.setUserId(custRequestDTO.getUserId());
        cust.setFirstName(custRequestDTO.getFirstName());
        cust.setLastName(custRequestDTO.getLastName());
        cust.setPhoneNumber(custRequestDTO.getPhoneNumber());
        cust.setAddressLine1(custRequestDTO.getAddressLine1());
        cust.setAddressLine2(custRequestDTO.getAddressLine2());
        cust.setCity(custRequestDTO.getCity());
        cust.setState(custRequestDTO.getState());
        cust.setPostalCode(custRequestDTO.getPostalCode());
        cust.setCountry(custRequestDTO.getCountry());
        cust.setDateOfBirth(LocalDate.parse(custRequestDTO.getDateOfBirth()));
        cust.setRegistrationDate(parseRegistrationTimestamp(custRequestDTO));
        return cust;
    }

    private static Instant parseRegistrationTimestamp(CustomerRequestDTO custRequestDTO) {
        String raw = custRequestDTO.getRegistrationDate();
        if (raw == null || raw.isBlank()) {
            raw = custRequestDTO.getRegisteredDate();
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Registration date is required");
        }

        // Allow both full ISO timestamp and legacy yyyy-MM-dd payloads.
        if (raw.length() == 10) {
            return LocalDate.parse(raw).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        }
        return Instant.parse(raw);
    }
}
