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
public class StudentService {
    private final StudentRepository students;
    private final DepartmentRepository departments;
    private final AcademicBatchRepository batches;
    private final AcademicSectionRepository sections;
    public StudentService(StudentRepository students, DepartmentRepository departments,
                          AcademicBatchRepository batches, AcademicSectionRepository sections) {
        this.students = students; this.departments = departments; this.batches = batches; this.sections = sections;
    }
    @Transactional
    public StudentResponse create(StudentRequest request) {
        if (students.existsByEnrollmentNumber(request.enrollmentNumber())) {
            throw new ConflictException("Enrollment number already exists.");
        }
        checkIdentifiers(request, null);
        Department department = department(request.departmentId());
        AcademicBatch batch = batch(request.batchId(), request.departmentId());
        AcademicSection section = section(request.sectionId(), request.batchId());
        return StudentResponse.from(students.saveAndFlush(new Student(request.enrollmentNumber(), request.rollNumber(),
                request.fullName(), request.email(), request.phoneNumber(), department, batch, section,
                request.currentSemester(), request.admissionYear())));
    }
    public PageResponse<StudentResponse> list(String name, String enrollmentNumber, Long departmentId, Long batchId,
                                              Long sectionId, Integer semester, StudentStatus status, Pageable pageable) {
        return PageResponse.from(students.search(name, enrollmentNumber, departmentId, batchId, sectionId,
                semester, status, pageable).map(StudentResponse::from));
    }
    public StudentResponse get(Long id) { return StudentResponse.from(require(id)); }
    @Transactional
    public StudentResponse update(Long id, StudentRequest request) {
        Student student = require(id);
        if (!student.getEnrollmentNumber().equals(request.enrollmentNumber())) {
            throw new ConflictException("Enrollment number cannot be changed.");
        }
        checkIdentifiers(request, id);
        Department department = department(request.departmentId());
        AcademicBatch batch = batch(request.batchId(), request.departmentId());
        AcademicSection section = section(request.sectionId(), request.batchId());
        student.update(request.rollNumber(), request.fullName(), request.email(), request.phoneNumber(), department,
                batch, section, request.currentSemester(), request.admissionYear());
        return StudentResponse.from(students.saveAndFlush(student));
    }
    @Transactional
    public StudentResponse updateStatus(Long id, StudentStatusRequest request) {
        Student student = require(id);
        student.setStatus(request.status());
        return StudentResponse.from(students.saveAndFlush(student));
    }
    private void checkIdentifiers(StudentRequest request, Long id) {
        boolean emailExists = id == null ? students.existsByEmail(request.email())
                : students.existsByEmailAndIdNot(request.email(), id);
        boolean rollExists = id == null ? students.existsByBatchIdAndRollNumber(request.batchId(), request.rollNumber())
                : students.existsByBatchIdAndRollNumberAndIdNot(request.batchId(), request.rollNumber(), id);
        if (emailExists) throw new ConflictException("Student email already exists.");
        if (rollExists) throw new ConflictException("Roll number already exists in this batch.");
    }
    private Student require(Long id) {
        return students.findById(id).orElseThrow(() -> new ResourceNotFoundException("Student not found."));
    }
    private Department department(Long id) {
        return departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found."));
    }
    private AcademicBatch batch(Long id, Long departmentId) {
        AcademicBatch batch = batches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Batch not found."));
        if (!batch.getDepartment().getId().equals(departmentId)) {
            throw new ConflictException("Batch does not belong to the selected department.");
        }
        return batch;
    }
    private AcademicSection section(Long id, Long batchId) {
        if (id == null) return null;
        AcademicSection section = sections.findById(id).orElseThrow(() -> new ResourceNotFoundException("Section not found."));
        if (!section.getBatch().getId().equals(batchId)) {
            throw new ConflictException("Section does not belong to the selected batch.");
        }
        return section;
    }
}
