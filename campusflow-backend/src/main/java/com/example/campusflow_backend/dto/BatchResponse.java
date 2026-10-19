package com.example.campusflow_backend.dto;

import com.example.campusflow_backend.entity.AcademicBatch;
import java.time.Instant;

public record BatchResponse(Long id, String name, int startYear, int endYear, Long departmentId,
                            boolean active, Instant createdAt, Instant updatedAt) {
    public static BatchResponse from(AcademicBatch batch) {
        return new BatchResponse(batch.getId(), batch.getName(), batch.getStartYear(), batch.getEndYear(),
                batch.getDepartment().getId(), batch.isActive(), batch.getCreatedAt(), batch.getUpdatedAt());
    }
}
