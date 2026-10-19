package com.example.campusflow_backend.repository;

import com.example.campusflow_backend.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByCode(String code);
    Page<Department> findByCode(String code, Pageable pageable);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
}
