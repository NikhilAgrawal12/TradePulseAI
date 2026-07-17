package com.tradepulse.authservice.dto.auth;

public class ForgotPasswordCodeVerifyResponseDTO {

    private final String message;
    private final String resetToken;
    private final Integer attemptsRemaining;

    public ForgotPasswordCodeVerifyResponseDTO(String message, String resetToken, Integer attemptsRemaining) {
        this.message = message;
        this.resetToken = resetToken;
        this.attemptsRemaining = attemptsRemaining;
    }

    public String getMessage() {
        return message;
    }

    public String getResetToken() {
        return resetToken;
    }

    public Integer getAttemptsRemaining() {
        return attemptsRemaining;
    }
}

