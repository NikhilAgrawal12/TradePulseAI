package com.tradepulseai.custservice.controller;

import com.tradepulseai.custservice.dto.customer.CustomerRequestDTO;
import com.tradepulseai.custservice.dto.customer.CustomerResponseDTO;
import com.tradepulseai.custservice.dto.validators.CreateCustomerValidationGroup;
import com.tradepulseai.custservice.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.groups.Default;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers")
@Tag(name = "Customers", description = "API for managing customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }


    @GetMapping("/email/{email}")
    @Operation(summary = "Get customer by email")
    public ResponseEntity<CustomerResponseDTO> getCustomerByEmail(@PathVariable String email) {
        CustomerResponseDTO customer = customerService.getCustomerByEmail(email);
        return ResponseEntity.ok().body(customer);
    }

    @PostMapping
    @Operation(summary = "Create a new customer")
    public ResponseEntity<CustomerResponseDTO> createUser(
            @Validated({Default.class, CreateCustomerValidationGroup.class}) @RequestBody CustomerRequestDTO customerRequestDTO
    ) {
        CustomerResponseDTO custResponseDTO = customerService.createCustomer(customerRequestDTO);
        return ResponseEntity.ok().body(custResponseDTO);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer")
    public ResponseEntity<CustomerResponseDTO> updateUser(
            @PathVariable Long id,
            @Validated({Default.class}) @RequestBody CustomerRequestDTO customerRequestDTO
    ) {
        CustomerResponseDTO custResponseDTO = customerService.updateCustomer(id, customerRequestDTO);
        return ResponseEntity.ok().body(custResponseDTO);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete customer")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
