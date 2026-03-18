package com.procurehub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RoleUpdateRequest {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Pattern(regexp = "ROLE_(USER|MANAGER|ADMIN)", message = "Role must be ROLE_USER, ROLE_MANAGER or ROLE_ADMIN")
    private String role;

    public RoleUpdateRequest() {
    }

    public RoleUpdateRequest(String email, String role) {
        this.email = email;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
