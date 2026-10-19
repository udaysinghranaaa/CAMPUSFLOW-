package com.example.campusflow_backend.dto;

import com.example.campusflow_backend.entity.StudentStatus;
import jakarta.validation.constraints.NotNull;

public record StudentStatusRequest(@NotNull StudentStatus status) { }
