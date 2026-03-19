package com.tradepulseai.custservice.controller;

import com.tradepulseai.custservice.dto.CustomerResponseDTO;
import com.tradepulseai.custservice.repository.CustomerRepository;
import com.tradepulseai.custservice.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/customers")
public class CustomerController {
    private final CustomerService customerservice;

    public CustomerController(CustomerService customerservice) {
        this.customerservice = customerservice;
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponseDTO>> getCustomers() {
        List<CustomerResponseDTO> users = customerservice.getCustomers();
        return ResponseEntity.ok().body(users);
    }


}
