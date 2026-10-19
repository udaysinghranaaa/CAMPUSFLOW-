package com.example.campusflow_backend.dto;

import com.example.campusflow_backend.entity.Student;
import com.example.campusflow_backend.entity.StudentStatus;
import java.time.Instant;

public record StudentResponse(Long id, String enrollmentNumber, String rollNumber, String fullName, String email,
                              String phoneNumber, Long departmentId, Long batchId, Long sectionId,
                              int currentSemester, int admissionYear, StudentStatus status,
                              Instant createdAt, Instant updatedAt) {
    public static StudentResponse from(Student student) {
        return new StudentResponse(student.getId(), student.getEnrollmentNumber(), student.getRollNumber(),
                student.getFullName(), student.getEmail(), student.getPhoneNumber(), student.getDepartment().getId(),
                student.getBatch().getId(), student.getSection() == null ? null : student.getSection().getId(),
                student.getCurrentSemester(), student.getAdmissionYear(), student.getStatus(),
                student.getCreatedAt(), student.getUpdatedAt());
    }
}
