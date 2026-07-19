package com.tradepulse.portfolioservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CustomerClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerClient.class);
    private static final String USER_ID_HEADER = "X-User-Id";

    private final RestClient restClient;

    public CustomerClient(
            @Value("${customer.service.base-url:http://customer-service:4000}") String customerServiceBaseUrl
    ) {
        this.restClient = RestClient.builder().baseUrl(customerServiceBaseUrl).build();
    }

    /**
     * Fetch customer data by userId.
     * Customer-service requires X-User-Id to authorize access to /customers/user/{userId}.
     */
    public CustomerInfo getCustomer(Long userId) {
        try {
            CustomerResponse response = restClient.get()
                    .uri("/customers/user/{userId}", userId)
                    .header(USER_ID_HEADER, String.valueOf(userId))
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

