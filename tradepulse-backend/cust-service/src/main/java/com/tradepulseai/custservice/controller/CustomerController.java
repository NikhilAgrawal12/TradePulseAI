package com.tradepulseai.custservice.controller;

import com.tradepulseai.custservice.dto.customer.CustomerRequestDTO;
import com.tradepulseai.custservice.dto.customer.CustomerRegistrationRequestDTO;
import com.tradepulseai.custservice.dto.customer.CustomerResponseDTO;
import com.tradepulseai.custservice.dto.validators.CreateCustomerValidationGroup;
import com.tradepulseai.custservice.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.groups.Default;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers")
@Tag(name = "Customers", description = "API for managing customers")
public class CustomerController {
    private static final String USER_ID_HEADER = "X-User-Id";

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }


    @GetMapping("/user/{userId}")
    @Operation(summary = "Get customer by user id")
    public ResponseEntity<CustomerResponseDTO> getCustomerByUserId(
            @RequestHeader(USER_ID_HEADER) String authenticatedUserId,
            @PathVariable Long userId
    ) {
        authorizePathUserId(authenticatedUserId, userId);
        CustomerResponseDTO customer = customerService.getCustomerByUserId(userId);
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

    @PostMapping("/register")
    @Operation(summary = "Register auth user and customer profile in one saga")
    public ResponseEntity<CustomerResponseDTO> registerCustomer(
            @Validated({Default.class}) @RequestBody CustomerRegistrationRequestDTO requestDTO
    ) {
        CustomerResponseDTO responseDTO = customerService.registerCustomer(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update customer")
    public ResponseEntity<CustomerResponseDTO> updateUser(
            @RequestHeader(USER_ID_HEADER) String authenticatedUserId,
            @PathVariable Long userId,
            @Validated({Default.class}) @RequestBody CustomerRequestDTO customerRequestDTO
    ) {
        authorizePathUserId(authenticatedUserId, userId);
        CustomerResponseDTO custResponseDTO = customerService.updateCustomer(userId, customerRequestDTO);
        return ResponseEntity.ok().body(custResponseDTO);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete customer")
    public ResponseEntity<Void> deleteUser(
            @RequestHeader(USER_ID_HEADER) String authenticatedUserId,
            @PathVariable Long userId
    ) {
        authorizePathUserId(authenticatedUserId, userId);
        customerService.deleteCustomer(userId);
        return ResponseEntity.noContent().build();
    }

    private void authorizePathUserId(String authenticatedUserId, Long pathUserId) {
        if (authenticatedUserId == null || authenticatedUserId.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required header: " + USER_ID_HEADER);
        }

        final Long normalizedAuthenticatedUserId;
        try {
            normalizedAuthenticatedUserId = Long.parseLong(authenticatedUserId.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid userId format in header " + USER_ID_HEADER + ": " + authenticatedUserId);
        }

        if (!normalizedAuthenticatedUserId.equals(pathUserId)) {
            throw new IllegalArgumentException("You are not allowed to access another user's customer profile.");
        }
    }
}
