package com.tradepulseai.authservice.dto.credentials;

public class UpdateCredentialsResponseDTO {
    private Long userId;
    private String email;
    private String token;

    public UpdateCredentialsResponseDTO() {
    }

    public UpdateCredentialsResponseDTO(Long userId, String email, String token) {
        this.userId = userId;
        this.email = email;
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}

