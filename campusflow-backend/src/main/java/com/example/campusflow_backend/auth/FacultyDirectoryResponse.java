package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.entity.Faculty;
public record FacultyDirectoryResponse(Long id,String fullName,String designation,String departmentCode,boolean active) {
    public static FacultyDirectoryResponse from(Faculty f) {
        return new FacultyDirectoryResponse(f.getId(),f.getFullName(),f.getDesignation(),f.getDepartment().getCode(),f.isActive());
    }
}
