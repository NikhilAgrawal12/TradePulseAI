package com.tradepulse.notificationservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    private final RestClient restClient;

    public AuthServiceClient(
            @Value("${auth.service.base-url:http://auth-service:4005}") String authServiceBaseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceBaseUrl)
                .build();
    }

    /**
     * Fetches user email from auth-service by userId.
     * Returns null if the user is not found or the service is unavailable.
     */
    public String getEmailByUserId(Long userId) {
        try {
            AuthUser user = restClient.get()
                    .uri("/users/{userId}", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalArgumentException("User not found for id: " + userId);
                    })
                    .body(AuthUser.class);
            return user != null ? user.email() : null;
        } catch (Exception ex) {
            log.warn("Could not fetch email for userId={}: {}", userId, ex.getMessage());
            return null;
        }
    }

    public record AuthUser(Long userId, String email, String role) {}
}

