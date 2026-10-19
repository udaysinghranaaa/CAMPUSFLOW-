package com.example.campusflow_backend.repository;

import com.example.campusflow_backend.entity.AcademicSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AcademicSectionRepository extends JpaRepository<AcademicSection, Long> {
    boolean existsByBatchId(Long batchId);
    boolean existsByBatchIdAndNameIgnoreCase(Long batchId, String name);
    boolean existsByBatchIdAndNameIgnoreCaseAndIdNot(Long batchId, String name, Long id);
    Page<AcademicSection> findByBatchId(Long batchId, Pageable pageable);
}
