package com.tradepulse.notificationservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CustomerServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceClient.class);

    private final RestClient restClient;

    public CustomerServiceClient(
            @Value("${customer.service.base-url:http://customer-service:4000}") String customerServiceBaseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(customerServiceBaseUrl)
                .build();
    }

    /**
     * Fetches the customer's first and last name from customer-service by userId.
     * Returns a CustomerName with empty strings if not found or service unavailable.
     */
    public CustomerName getNameByUserId(Long userId) {
        try {
            CustomerProfile profile = restClient.get()
                    .uri("/customers/user/{userId}", userId)
                    .header("X-User-Id", String.valueOf(userId))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalArgumentException("Customer not found for userId: " + userId);
                    })
                    .body(CustomerProfile.class);
            if (profile == null) {
                return new CustomerName("", "");
            }
            return new CustomerName(
                    profile.firstName() != null ? profile.firstName() : "",
                    profile.lastName()  != null ? profile.lastName()  : ""
            );
        } catch (Exception ex) {
            log.warn("Could not fetch name for userId={}: {}", userId, ex.getMessage());
            return new CustomerName("", "");
        }
    }

    public record CustomerName(String firstName, String lastName) {}

    // Partial mapping — only the fields we need
    private record CustomerProfile(String firstName, String lastName) {}
}


