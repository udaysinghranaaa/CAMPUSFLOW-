package com.example.campusflow_backend.service;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.exception.*;
import com.example.campusflow_backend.repository.*;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AcademicBatchService {
    private final AcademicBatchRepository batches;
    private final DepartmentRepository departments;
    private final AcademicSectionRepository sections;
    private final StudentRepository students;
    public AcademicBatchService(AcademicBatchRepository batches, DepartmentRepository departments,
                                AcademicSectionRepository sections, StudentRepository students) {
        this.batches = batches; this.departments = departments; this.sections = sections; this.students = students;
    }
    @Transactional
    public BatchResponse create(BatchRequest request) {
        Department department = department(request.departmentId());
        if (batches.existsByDepartmentIdAndNameIgnoreCase(request.departmentId(), request.name())) {
            throw new ConflictException("Batch name already exists in this department.");
        }
        return BatchResponse.from(batches.saveAndFlush(new AcademicBatch(request.name(), request.startYear(),
                request.endYear(), department, request.active())));
    }
    @PreAuthorize("isAuthenticated()")
    public PageResponse<BatchResponse> list(Long departmentId, Pageable pageable) {
        var result = departmentId == null ? batches.findAll(pageable) : batches.findByDepartmentId(departmentId, pageable);
        return PageResponse.from(result.map(BatchResponse::from));
    }
    @PreAuthorize("isAuthenticated()")
    public BatchResponse get(Long id) { return BatchResponse.from(require(id)); }
    @Transactional
    public BatchResponse update(Long id, BatchRequest request) {
        AcademicBatch batch = require(id);
        Department department = department(request.departmentId());
        if (!batch.getDepartment().getId().equals(request.departmentId()) && students.existsByBatchId(id)) {
            throw new ConflictException("Cannot change the department of a batch with students.");
        }
        if (batches.existsByDepartmentIdAndNameIgnoreCaseAndIdNot(request.departmentId(), request.name(), id)) {
            throw new ConflictException("Batch name already exists in this department.");
        }
        batch.update(request.name(), request.startYear(), request.endYear(), department, request.active());
        return BatchResponse.from(batches.saveAndFlush(batch));
    }
    @Transactional
    public void delete(Long id) {
        AcademicBatch batch = require(id);
        if (students.existsByBatchId(id) || sections.existsByBatchId(id)) {
            throw new ConflictException("Batch has students or sections; preserve or reassign them before deletion.");
        }
        batches.delete(batch); batches.flush();
    }
    private AcademicBatch require(Long id) {
        return batches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Batch not found."));
    }
    private Department department(Long id) {
        return departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found."));
    }
}
