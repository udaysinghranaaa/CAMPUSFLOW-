package com.example.campusflow_backend.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record SectionRequest(@NotBlank @Size(max = 30) String name, @NotNull @Positive Long batchId,
                             @Positive Integer capacity, @NotNull Boolean active) {
    public SectionRequest { name = name == null ? null : name.strip().toUpperCase(Locale.ROOT); }
}
