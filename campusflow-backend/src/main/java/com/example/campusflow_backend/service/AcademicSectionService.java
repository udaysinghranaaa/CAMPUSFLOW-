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
public class AcademicSectionService {
    private final AcademicSectionRepository sections;
    private final AcademicBatchRepository batches;
    private final StudentRepository students;
    public AcademicSectionService(AcademicSectionRepository sections, AcademicBatchRepository batches, StudentRepository students) {
        this.sections = sections; this.batches = batches; this.students = students;
    }
    @Transactional
    public SectionResponse create(SectionRequest request) {
        AcademicBatch batch = batch(request.batchId());
        if (sections.existsByBatchIdAndNameIgnoreCase(request.batchId(), request.name())) {
            throw new ConflictException("Section name already exists in this batch.");
        }
        return SectionResponse.from(sections.saveAndFlush(
                new AcademicSection(request.name(), batch, request.capacity(), request.active())));
    }
    @PreAuthorize("isAuthenticated()")
    public PageResponse<SectionResponse> list(Long batchId, Pageable pageable) {
        var result = batchId == null ? sections.findAll(pageable) : sections.findByBatchId(batchId, pageable);
        return PageResponse.from(result.map(SectionResponse::from));
    }
    @PreAuthorize("isAuthenticated()")
    public SectionResponse get(Long id) { return SectionResponse.from(require(id)); }
    @Transactional
    public SectionResponse update(Long id, SectionRequest request) {
        AcademicSection section = require(id);
        AcademicBatch batch = batch(request.batchId());
        if (!section.getBatch().getId().equals(request.batchId()) && students.existsBySectionId(id)) {
            throw new ConflictException("Cannot change the batch of a section with students.");
        }
        if (sections.existsByBatchIdAndNameIgnoreCaseAndIdNot(request.batchId(), request.name(), id)) {
            throw new ConflictException("Section name already exists in this batch.");
        }
        section.update(request.name(), batch, request.capacity(), request.active());
        return SectionResponse.from(sections.saveAndFlush(section));
    }
    @Transactional
    public void delete(Long id) {
        AcademicSection section = require(id);
        if (students.existsBySectionId(id)) throw new ConflictException("Section has students; reassign them before deletion.");
        sections.delete(section); sections.flush();
    }
    private AcademicSection require(Long id) {
        return sections.findById(id).orElseThrow(() -> new ResourceNotFoundException("Section not found."));
    }
    private AcademicBatch batch(Long id) {
        return batches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Batch not found."));
    }
}
