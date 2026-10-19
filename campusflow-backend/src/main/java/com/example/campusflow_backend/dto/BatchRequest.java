package com.example.campusflow_backend.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record BatchRequest(@NotBlank @Size(max = 150) String name,
                           @NotNull @Min(1900) @Max(9999) Integer startYear,
                           @NotNull @Min(1900) @Max(9999) Integer endYear,
                           @NotNull @Positive Long departmentId, @NotNull Boolean active) {
    public BatchRequest { name = name == null ? null : name.strip(); }
    @AssertTrue(message = "endYear must be later than startYear")
    @JsonIgnore
    public boolean isYearRangeValid() { return startYear == null || endYear == null || endYear > startYear; }
}
