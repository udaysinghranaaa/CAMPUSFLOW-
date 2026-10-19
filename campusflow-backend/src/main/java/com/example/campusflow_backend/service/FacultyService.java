package com.example.campusflow_backend.service;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.entity.Department;
import com.example.campusflow_backend.entity.Faculty;
import com.example.campusflow_backend.exception.ConflictException;
import com.example.campusflow_backend.exception.ResourceNotFoundException;
import com.example.campusflow_backend.repository.DepartmentRepository;
import com.example.campusflow_backend.repository.FacultyRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class FacultyService {
    private final FacultyRepository faculty;
    private final DepartmentRepository departments;

    public FacultyService(FacultyRepository faculty, DepartmentRepository departments) {
        this.faculty = faculty;
        this.departments = departments;
    }

    @Transactional
    public FacultyResponse create(FacultyRequest request) {
        Department department = requireDepartment(request.departmentId());
        if (faculty.existsByEmail(request.email()) || faculty.existsByEmployeeCode(request.employeeCode())) {
            throw new ConflictException("Faculty email or employee code already exists.");
        }
        return FacultyResponse.from(faculty.saveAndFlush(new Faculty(request.fullName(), request.email(),
                request.employeeCode(), request.designation(), department)));
    }

    public PageResponse<FacultyResponse> list(Long departmentId, Boolean active, String name, Pageable pageable) {
        // Escape LIKE wildcards so a name search treats user input literally.
        String search = name == null || name.isBlank() ? null : name.strip()
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return PageResponse.from(faculty.search(departmentId, active, search, pageable)
                .map(FacultyResponse::from));
    }

    public FacultyResponse get(Long id) {
        return FacultyResponse.from(requireFaculty(id));
    }

    @Transactional
    public FacultyResponse update(Long id, FacultyRequest request) {
        Faculty record = requireFaculty(id);
        Department department = requireDepartment(request.departmentId());
        if (faculty.existsByEmailAndIdNot(request.email(), id)
                || faculty.existsByEmployeeCodeAndIdNot(request.employeeCode(), id)) {
            throw new ConflictException("Faculty email or employee code already exists.");
        }
        record.update(request.fullName(), request.email(), request.employeeCode(), request.designation(), department);
        return FacultyResponse.from(faculty.saveAndFlush(record));
    }

    @Transactional
    public FacultyResponse updateStatus(Long id, FacultyStatusRequest request) {
        Faculty record = requireFaculty(id);
        record.setActive(request.active());
        return FacultyResponse.from(faculty.saveAndFlush(record));
    }

    @Transactional
    public void delete(Long id) {
        faculty.delete(requireFaculty(id));
        faculty.flush();
    }

    private Faculty requireFaculty(Long id) {
        return faculty.findById(id).orElseThrow(() -> new ResourceNotFoundException("Faculty not found."));
    }

    private Department requireDepartment(Long id) {
        return departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found."));
    }
}
