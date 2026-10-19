package com.example.campusflow_backend.repository;

import com.example.campusflow_backend.entity.Student;
import com.example.campusflow_backend.entity.StudentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {
    boolean existsByEnrollmentNumber(String enrollmentNumber);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByBatchIdAndRollNumber(Long batchId, String rollNumber);
    boolean existsByBatchIdAndRollNumberAndIdNot(Long batchId, String rollNumber, Long id);
    boolean existsByBatchId(Long batchId);
    boolean existsBySectionId(Long sectionId);

    @Override
    @EntityGraph(attributePaths = {"department", "batch", "section"})
    Page<Student> findAll(Specification<Student> specification, Pageable pageable);

    default Page<Student> search(String name, String enrollmentNumber, Long departmentId, Long batchId,
                                 Long sectionId, Integer semester, StudentStatus status, Pageable pageable) {
        Specification<Student> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isBlank()) {
                String pattern = "%" + name.strip().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                predicates.add(builder.like(builder.lower(root.<String>get("fullName")), pattern, '!'));
            }
            if (enrollmentNumber != null && !enrollmentNumber.isBlank()) {
                predicates.add(builder.equal(root.<String>get("enrollmentNumber"),
                        enrollmentNumber.strip().toUpperCase(Locale.ROOT)));
            }
            if (departmentId != null) predicates.add(builder.equal(root.get("department").<Long>get("id"), departmentId));
            if (batchId != null) predicates.add(builder.equal(root.get("batch").<Long>get("id"), batchId));
            if (sectionId != null) predicates.add(builder.equal(root.get("section").<Long>get("id"), sectionId));
            if (semester != null) predicates.add(builder.equal(root.<Integer>get("currentSemester"), semester));
            if (status != null) predicates.add(builder.equal(root.<StudentStatus>get("status"), status));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return findAll(specification, pageable);
    }
}
