package com.example.campusflow_backend.dto;

import com.example.campusflow_backend.entity.AcademicSection;
import java.time.Instant;

public record SectionResponse(Long id, String name, Long batchId, Integer capacity,
                              boolean active, Instant createdAt, Instant updatedAt) {
    public static SectionResponse from(AcademicSection section) {
        return new SectionResponse(section.getId(), section.getName(), section.getBatch().getId(),
                section.getCapacity(), section.isActive(), section.getCreatedAt(), section.getUpdatedAt());
    }
}
