package com.tradepulse.authservice.dto.auth;

public class ForgotPasswordResponseDTO {

    private final String message;

    public ForgotPasswordResponseDTO(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}

