package com.example.campusflow_backend.repository;

import com.example.campusflow_backend.entity.AcademicBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AcademicBatchRepository extends JpaRepository<AcademicBatch, Long> {
    boolean existsByDepartmentIdAndNameIgnoreCase(Long departmentId, String name);
    boolean existsByDepartmentIdAndNameIgnoreCaseAndIdNot(Long departmentId, String name, Long id);
    Page<AcademicBatch> findByDepartmentId(Long departmentId, Pageable pageable);
}
