package com.procurehub.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdateUserRequest {

    @Email(message = "email must be valid")
    @NotBlank(message = "email is required")
    private String email;

    @NotBlank(message = "role is required")
    @Pattern(
            regexp = "ROLE_(USER|MANAGER|ADMIN)",
            message = "role must be ROLE_USER, ROLE_MANAGER or ROLE_ADMIN"
    )
    private String role;

    private boolean emailVerified;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}

