package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.entity.AccountRole;
import jakarta.validation.constraints.*;
import java.util.Locale;
public record ProvisionAccountRequest(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=12,max=72) String initialPassword,@NotNull AccountRole role,
        @Positive Long studentId,@Positive Long facultyId) {
    public ProvisionAccountRequest { email=email==null?null:email.strip().toLowerCase(Locale.ROOT); }
    @Override public String toString() { return "ProvisionAccountRequest[REDACTED]"; }
}
