package com.example.campusflow_backend.auth;
import jakarta.validation.constraints.*;
public record PasswordChangeRequest(@NotBlank @Size(max=72) String currentPassword,
                                    @NotBlank @Size(min=12,max=72) String newPassword) {
    @Override public String toString() { return "PasswordChangeRequest[REDACTED]"; }
}
