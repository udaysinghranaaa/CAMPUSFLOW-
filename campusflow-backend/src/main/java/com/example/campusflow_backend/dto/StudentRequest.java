package com.example.campusflow_backend.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record StudentRequest(
        @NotBlank @Size(max = 40) String enrollmentNumber,
        @NotBlank @Size(max = 30) String rollNumber,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 25) @Pattern(regexp = "[+0-9() .-]*", message = "invalid phone number format") String phoneNumber,
        @NotNull @Positive Long departmentId, @NotNull @Positive Long batchId, @Positive Long sectionId,
        @NotNull @Positive Integer currentSemester, @NotNull @Min(1900) @Max(9999) Integer admissionYear) {
    public StudentRequest {
        enrollmentNumber = enrollmentNumber == null ? null : enrollmentNumber.strip().toUpperCase(Locale.ROOT);
        rollNumber = rollNumber == null ? null : rollNumber.strip().toUpperCase(Locale.ROOT);
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        phoneNumber = phoneNumber == null ? null : phoneNumber.strip();
    }
}
