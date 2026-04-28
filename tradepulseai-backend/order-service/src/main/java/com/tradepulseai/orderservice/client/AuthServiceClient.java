package com.tradepulseai.orderservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AuthServiceClient {

    private final RestClient restClient;

    public AuthServiceClient(@Value("${auth.service.base-url:http://auth-service:4005}") String authServiceBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceBaseUrl)
                .build();
    }

    public AuthUser getUserById(Long userId) {
        return restClient.get()
                .uri("/users/{userId}", userId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalArgumentException("User not found for id: " + userId);
                })
                .body(AuthUser.class);
    }

    public record AuthUser(Long userId, String email, String role) {
    }
}

