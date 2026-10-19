package com.example.campusflow_backend.service;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.entity.Department;
import com.example.campusflow_backend.exception.ConflictException;
import com.example.campusflow_backend.exception.ResourceNotFoundException;
import com.example.campusflow_backend.repository.DepartmentRepository;
import com.example.campusflow_backend.repository.FacultyRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class DepartmentService {
    private final DepartmentRepository departments;
    private final FacultyRepository faculty;

    public DepartmentService(DepartmentRepository departments, FacultyRepository faculty) {
        this.departments = departments;
        this.faculty = faculty;
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        if (departments.existsByCode(request.code())) {
            throw new ConflictException("Department code already exists.");
        }
        return DepartmentResponse.from(departments.saveAndFlush(
                new Department(request.name(), request.code(), request.description())));
    }

    @PreAuthorize("isAuthenticated()")
    public PageResponse<DepartmentResponse> list(String code, Pageable pageable) {
        var result = code == null || code.isBlank()
                ? departments.findAll(pageable)
                : departments.findByCode(code.strip().toUpperCase(Locale.ROOT), pageable);
        return PageResponse.from(result.map(DepartmentResponse::from));
    }

    @PreAuthorize("isAuthenticated()")
    public DepartmentResponse get(Long id) {
        return DepartmentResponse.from(requireDepartment(id));
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request) {
        Department department = requireDepartment(id);
        if (departments.existsByCodeAndIdNot(request.code(), id)) {
            throw new ConflictException("Department code already exists.");
        }
        department.update(request.name(), request.code(), request.description());
        return DepartmentResponse.from(departments.saveAndFlush(department));
    }

    @Transactional
    public void delete(Long id) {
        Department department = requireDepartment(id);
        if (faculty.existsByDepartmentId(id)) {
            throw new ConflictException("Department has faculty records; reassign or delete them first.");
        }
        departments.delete(department);
        departments.flush();
    }

    private Department requireDepartment(Long id) {
        return departments.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Department not found."));
    }
}
