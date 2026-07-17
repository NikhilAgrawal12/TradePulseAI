package com.tradepulseai.apigateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private static final String AUTHENTICATED_USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final WebClient authServiceClient;
    private final WebClient customerServiceClient;

    public ProfileController(
            WebClient.Builder webClientBuilder,
            @Value("${auth.service.url:http://auth-service:4005}") String authServiceUrl,
            @Value("${customer.service.url:http://cust-service:4000}") String customerServiceUrl
    ) {
        this.authServiceClient = webClientBuilder.baseUrl(authServiceUrl).build();
        this.customerServiceClient = webClientBuilder.baseUrl(customerServiceUrl).build();
    }

    @GetMapping("/profile")
    public Mono<ResponseEntity<Object>> getProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body((Object) Map.of("message", "Missing or invalid Authorization header")));
        }

        return validateAndExtractUserId(authHeader)
                .flatMap(userId -> Mono.zip(
                        fetchCustomerProfile(userId),
                        fetchCredentials(userId, authHeader)
                ).map(tuple -> ResponseEntity.ok((Object) mergeProfile(tuple.getT1(), tuple.getT2()))))
                .onErrorResume(WebClientResponseException.class, ex -> Mono.just(
                        ResponseEntity.status(ex.getStatusCode())
                                .body((Object) Map.of("message", resolveErrorMessage(ex)))
                ))
                .onErrorResume(IllegalArgumentException.class, ex -> Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body((Object) Map.of("message", ex.getMessage()))
                ));
    }

    private Mono<Long> validateAndExtractUserId(String authHeader) {
        return authServiceClient.get()
                .uri("/validate")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .toBodilessEntity()
                .map(response -> {
                    String authenticatedUserId = response.getHeaders().getFirst(AUTHENTICATED_USER_ID_HEADER);
                    if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
                        throw new IllegalArgumentException("Authenticated user id is missing");
                    }

                    try {
                        return Long.parseLong(authenticatedUserId);
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Authenticated user id is invalid");
                    }
                });
    }

    private Mono<CustomerProfileResponse> fetchCustomerProfile(Long userId) {
        return customerServiceClient.get()
                .uri("/customers/user/{userId}", userId)
                .header(USER_ID_HEADER, String.valueOf(userId))
                .retrieve()
                .bodyToMono(CustomerProfileResponse.class);
    }

    private Mono<CredentialsResponse> fetchCredentials(Long userId, String authHeader) {
        return authServiceClient.get()
                .uri("/users/{userId}/credentials", userId)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .bodyToMono(CredentialsResponse.class);
    }

    private ProfileResponse mergeProfile(CustomerProfileResponse customerProfile, CredentialsResponse credentials) {
        return new ProfileResponse(
                customerProfile.userId() != null ? customerProfile.userId() : credentials.userId(),
                customerProfile.customerId(),
                customerProfile.firstName(),
                customerProfile.lastName(),
                credentials.email(),
                customerProfile.phoneNumber(),
                customerProfile.addressLine1(),
                customerProfile.addressLine2(),
                customerProfile.city(),
                customerProfile.state(),
                customerProfile.postalCode(),
                customerProfile.country(),
                customerProfile.dateOfBirth()
        );
    }

    private String resolveErrorMessage(WebClientResponseException ex) {
        String responseBody = ex.getResponseBodyAsString();
        if (!responseBody.isBlank()) {
            return responseBody;
        }
        return "Unable to load profile";
    }

    private record CustomerProfileResponse(
            Long userId,
            Long customerId,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country,
            String dateOfBirth
    ) {
    }

    private record CredentialsResponse(Long userId, String email) {
    }

    private record ProfileResponse(
            Long userId,
            Long customerId,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country,
            String dateOfBirth
    ) {
    }
}




