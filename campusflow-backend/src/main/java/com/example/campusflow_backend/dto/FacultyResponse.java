package com.example.campusflow_backend.dto;

import com.example.campusflow_backend.entity.Faculty;
import java.time.Instant;

public record FacultyResponse(Long id, String fullName, String email, String employeeCode,
                              String designation, Long departmentId, String departmentCode,
                              boolean active, Instant createdAt, Instant updatedAt) {
    public static FacultyResponse from(Faculty faculty) {
        return new FacultyResponse(faculty.getId(), faculty.getFullName(), faculty.getEmail(),
                faculty.getEmployeeCode(), faculty.getDesignation(), faculty.getDepartment().getId(),
                faculty.getDepartment().getCode(), faculty.isActive(),
                faculty.getCreatedAt(), faculty.getUpdatedAt());
    }
}
