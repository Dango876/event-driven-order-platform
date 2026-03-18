package com.procurehub.auth.dto;

public class AuthResponse {

    private final String message;
    private final String email;
    private final String accessToken;
    private final String refreshToken;

    public AuthResponse(String message, String email, String accessToken, String refreshToken) {
        this.message = message;
        this.email = email;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getMessage() {
        return message;
    }

    public String getEmail() {
        return email;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
