package com.example.campusflow_backend.auth;
import java.nio.charset.StandardCharsets;
import com.example.campusflow_backend.exception.InvalidRequestException;

public final class PasswordPolicy {
    private PasswordPolicy() { }
    public static void validate(String password) {
        if(password==null || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72 || password.isBlank())
            throw new InvalidRequestException("Password must be 12 or more characters and at most 72 UTF-8 bytes.");
    }
}
