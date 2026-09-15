package com.tradepulse.customerservice.controller;

import com.tradepulse.customerservice.dto.customer.CustomerRegistrationRequestDTO;
import com.tradepulse.customerservice.dto.customer.CustomerRequestDTO;
import com.tradepulse.customerservice.dto.customer.CustomerResponseDTO;
import com.tradepulse.customerservice.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.groups.Default;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @Operation(summary = "Get customer by user id (internal service-to-service use)")
    public ResponseEntity<CustomerResponseDTO> getCustomerByUserId(
            @RequestHeader(USER_ID_HEADER) String authenticatedUserId,
            @PathVariable Long userId
    ) {
        authorizePathUserId(authenticatedUserId, userId);
        CustomerResponseDTO customer = customerService.getCustomerByUserId(userId);
        return ResponseEntity.ok().body(customer);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated customer")
    public ResponseEntity<CustomerResponseDTO> getCurrentCustomer(
            @RequestHeader(USER_ID_HEADER) String authenticatedUserId
    ) {
        CustomerResponseDTO customer = customerService.getCustomerByUserId(normalizeUserId(authenticatedUserId));
        return ResponseEntity.ok().body(customer);
    }

    @PutMapping("/me")
    @Operation(summary = "Update current authenticated customer")
    public ResponseEntity<CustomerResponseDTO> updateCurrentCustomer(
            @RequestHeader(USER_ID_HEADER) String authenticatedUserId,
            @Validated({Default.class}) @RequestBody CustomerRequestDTO customerRequestDTO
    ) {
        Long userId = normalizeUserId(authenticatedUserId);
        CustomerResponseDTO custResponseDTO = customerService.updateCustomer(userId, customerRequestDTO);
        return ResponseEntity.ok().body(custResponseDTO);
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete current authenticated account")
    public ResponseEntity<Void> deleteCurrentCustomer(
            @RequestHeader(USER_ID_HEADER) String authenticatedUserId
    ) {
        customerService.deleteCurrentAccount(normalizeUserId(authenticatedUserId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    @Operation(summary = "Register auth user and customer profile in one saga")
    public ResponseEntity<CustomerResponseDTO> registerCustomer(
            @Validated({Default.class}) @RequestBody CustomerRegistrationRequestDTO requestDTO
    ) {
        CustomerResponseDTO responseDTO = customerService.registerCustomer(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    private void authorizePathUserId(String authenticatedUserId, Long pathUserId) {
        Long normalizedAuthenticatedUserId = normalizeUserId(authenticatedUserId);
        if (!normalizedAuthenticatedUserId.equals(pathUserId)) {
            throw new IllegalArgumentException("You are not allowed to access another user's customer profile.");
        }
    }

    private Long normalizeUserId(String userIdHeaderValue) {
        if (userIdHeaderValue == null || userIdHeaderValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required header: " + USER_ID_HEADER);
        }

        try {
            return Long.parseLong(userIdHeaderValue.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid userId format in header " + USER_ID_HEADER + ": " + userIdHeaderValue);
        }
    }
}
