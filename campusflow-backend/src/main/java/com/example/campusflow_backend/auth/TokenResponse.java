package com.example.campusflow_backend.auth;
public record TokenResponse(String tokenType,String accessToken,long expiresIn,String refreshToken) {
    @Override public String toString() { return "TokenResponse[REDACTED]"; }
}
