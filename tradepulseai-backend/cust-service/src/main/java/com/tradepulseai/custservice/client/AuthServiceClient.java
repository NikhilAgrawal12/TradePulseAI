package com.tradepulseai.custservice.client;

import com.tradepulseai.custservice.dto.customer.CustomerRegistrationRequestDTO;
import com.tradepulseai.custservice.exception.EmailAlreadyExistsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AuthServiceClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String internalApiKey;

    public AuthServiceClient(
            @Value("${auth.service.base-url:http://auth-service:4005}") String authServiceBaseUrl,
            @Value("${auth.service.internal-api-key:}") String internalApiKey
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceBaseUrl)
                .build();
        this.internalApiKey = internalApiKey;
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
                .onStatus(status -> status.value() == 409, (requestSpec, response) -> {
                    throw new EmailAlreadyExistsException("Email already exists");
                })
                .onStatus(HttpStatusCode::is4xxClientError, (requestSpec, response) -> {
                    throw new IllegalArgumentException("Invalid registration data for auth user: " + request.getEmail());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (requestSpec, response) -> {
                    throw new IllegalStateException("Auth service is unavailable for registration: " + request.getEmail());
                })
                .onStatus(HttpStatusCode::isError, (requestSpec, response) -> {
                    throw new IllegalArgumentException("Unable to create auth user for email: " + request.getEmail());
                })
                .body(AuthUser.class);
    }

    public void deleteUserById(Long userId) {
        restClient.delete()
                .uri("/users/{userId}", userId)
                .headers(headers -> {
                    if (internalApiKey != null && !internalApiKey.isBlank()) {
                        headers.set(INTERNAL_API_KEY_HEADER, internalApiKey);
                    }
                })
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
