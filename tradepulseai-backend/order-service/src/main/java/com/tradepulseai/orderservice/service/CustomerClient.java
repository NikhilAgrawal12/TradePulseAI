package com.tradepulseai.orderservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CustomerClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerClient.class);

    private final RestClient restClient;

    public CustomerClient(
            @Value("${cust.service.base-url:http://cust-service:4001}") String custServiceBaseUrl
    ) {
        this.restClient = RestClient.builder().baseUrl(custServiceBaseUrl).build();
    }

    /**
     * Fetch customer data by userId
     * This call doesn't require authentication headers for inter-service communication
     */
    public CustomerInfo getCustomer(Long userId) {
        try {
            CustomerResponse response = restClient.get()
                    .uri("/customers/user/{userId}", userId)
                    .retrieve()
                    .body(CustomerResponse.class);

            if (response != null) {
                return new CustomerInfo(
                        response.firstName != null ? response.firstName : "",
                        response.lastName != null ? response.lastName : ""
                );
            }
        } catch (Exception ex) {
            log.warn("Unable to fetch customer data for userId={}: {}", userId, ex.getMessage());
        }
        return new CustomerInfo("", "");
    }

    public record CustomerInfo(String firstName, String lastName) {}

    @SuppressWarnings("unused")
    private static class CustomerResponse {
        public Long userId;
        public String firstName;
        public String lastName;
        public String email;
        public String phoneNumber;
    }
}

