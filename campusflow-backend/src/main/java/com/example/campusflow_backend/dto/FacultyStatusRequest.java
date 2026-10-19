package com.example.campusflow_backend.dto;

import jakarta.validation.constraints.NotNull;

public record FacultyStatusRequest(@NotNull Boolean active) { }
