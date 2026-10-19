package com.example.campusflow_backend.repository;

import com.example.campusflow_backend.entity.Faculty;
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

public interface FacultyRepository extends JpaRepository<Faculty, Long>, JpaSpecificationExecutor<Faculty> {
    boolean existsByEmail(String email);
    boolean existsByEmployeeCode(String employeeCode);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByEmployeeCodeAndIdNot(String employeeCode, Long id);
    boolean existsByDepartmentId(Long departmentId);

    @Override
    @EntityGraph(attributePaths = "department")
    Page<Faculty> findAll(Specification<Faculty> specification, Pageable pageable);

    /** The service trims the name and escapes LIKE wildcards with '!'. */
    default Page<Faculty> search(Long departmentId, Boolean active, String name, Pageable pageable) {
        Specification<Faculty> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (departmentId != null) {
                predicates.add(builder.equal(root.get("department").<Long>get("id"), departmentId));
            }
            if (active != null) {
                predicates.add(builder.equal(root.<Boolean>get("active"), active));
            }
            if (name != null && !name.isBlank()) {
                String pattern = "%" + name.toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.like(builder.lower(root.<String>get("fullName")), pattern, '!'));
            }
            // Absent filters do not produce null JDBC parameters or SQL function calls.
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return findAll(specification, pageable);
    }
}
