package com.tradepulseai.authservice.dto.auth;

public class ForgotPasswordCodeRequestResponseDTO {

    private final String message;
    private final int expiresInSeconds;
    private final int maxAttempts;

    public ForgotPasswordCodeRequestResponseDTO(String message, int expiresInSeconds, int maxAttempts) {
        this.message = message;
        this.expiresInSeconds = expiresInSeconds;
        this.maxAttempts = maxAttempts;
    }

    public String getMessage() {
        return message;
    }

    public int getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}

