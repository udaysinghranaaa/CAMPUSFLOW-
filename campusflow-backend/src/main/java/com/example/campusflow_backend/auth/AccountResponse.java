package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.entity.*;
import java.time.Instant;
public record AccountResponse(Long id,String email,AccountRole role,boolean enabled,Long studentId,Long facultyId,
                              Instant createdAt,Instant updatedAt) {
    public static AccountResponse from(UserAccount u) {
        return new AccountResponse(u.getId(),u.getEmail(),u.getRole(),u.isEnabled(),
                u.getStudent()==null?null:u.getStudent().getId(),u.getFaculty()==null?null:u.getFaculty().getId(),
                u.getCreatedAt(),u.getUpdatedAt());
    }
}
