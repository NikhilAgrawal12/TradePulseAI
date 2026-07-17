package com.tradepulseai.authservice.dto.credentials;

import com.tradepulseai.authservice.dto.validators.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public class ChangePasswordRequestDTO {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @ValidPassword(message = "Password must be at least 8 characters and include uppercase, lowercase, number, and special character")
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}

