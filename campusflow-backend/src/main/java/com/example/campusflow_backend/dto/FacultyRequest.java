package com.example.campusflow_backend.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record FacultyRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 30) String employeeCode,
        @Size(max = 100) String designation,
        @NotNull @Positive Long departmentId) {
    public FacultyRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        employeeCode = employeeCode == null ? null : employeeCode.strip().toUpperCase(Locale.ROOT);
        designation = designation == null ? null : designation.strip();
    }
}
