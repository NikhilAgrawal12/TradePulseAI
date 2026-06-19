package com.tradepulseai.custservice.client;

import com.tradepulseai.custservice.dto.customer.CustomerRegistrationRequestDTO;
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

    public AuthUser getUserByEmail(String email) {
        return restClient.get()
                .uri("/users/email/{email}", email)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalArgumentException("User not found for email: " + email);
                })
                .body(AuthUser.class);
    }

    public AuthUser registerUser(CustomerRegistrationRequestDTO request) {
        RegisterPayload payload = new RegisterPayload(request.getEmail(), request.getPassword());
        return restClient.post()
                .uri("/register")
                .body(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (requestSpec, response) -> {
                    throw new IllegalArgumentException("Unable to create auth user for email: " + request.getEmail());
                })
                .body(AuthUser.class);
    }

    public void deleteUserById(Long userId) {
        restClient.delete()
                .uri("/users/{userId}", userId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("Unable to rollback auth user with id: " + userId);
                })
                .toBodilessEntity();
    }

    private record RegisterPayload(String email, String password) {
    }

    public record AuthUser(Long userId, String email, String role) {
    }
}
