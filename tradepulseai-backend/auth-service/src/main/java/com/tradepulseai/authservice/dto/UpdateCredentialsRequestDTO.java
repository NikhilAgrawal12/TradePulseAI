package com.tradepulseai.authservice.dto;

import jakarta.validation.constraints.Email;

public class UpdateCredentialsRequestDTO {
    @Email(message = "Email must be valid")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

