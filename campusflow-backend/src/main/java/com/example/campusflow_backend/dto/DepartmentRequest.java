package com.example.campusflow_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record DepartmentRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 20) String code,
        @Size(max = 1000) String description) {
    public DepartmentRequest {
        name = name == null ? null : name.strip();
        code = code == null ? null : code.strip().toUpperCase(Locale.ROOT);
        description = description == null ? null : description.strip();
    }
}
