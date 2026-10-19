package com.example.campusflow_backend.auth;
import jakarta.validation.constraints.NotNull;
public record AccountStatusRequest(@NotNull Boolean enabled) { }
