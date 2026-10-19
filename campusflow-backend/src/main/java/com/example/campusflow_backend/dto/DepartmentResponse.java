package com.example.campusflow_backend.dto;

import com.example.campusflow_backend.entity.Department;
import java.time.Instant;

public record DepartmentResponse(Long id, String name, String code, String description,
                                 Instant createdAt, Instant updatedAt) {
    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.getId(), department.getName(), department.getCode(),
                department.getDescription(), department.getCreatedAt(), department.getUpdatedAt());
    }
}
