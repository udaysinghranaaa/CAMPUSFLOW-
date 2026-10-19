package com.example.campusflow_backend.timetable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface TimetableSessionRepository extends JpaRepository<TimetableSession,Long>, JpaSpecificationExecutor<TimetableSession> { @EntityGraph(attributePaths={"term","section","subject","faculty","room"}) org.springframework.data.domain.Page<TimetableSession> findAll(org.springframework.data.jpa.domain.Specification<TimetableSession> spec,org.springframework.data.domain.Pageable pageable); @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from TimetableSession s where s.id=:id") Optional<TimetableSession> lock(@Param("id") Long id); boolean existsBySubjectId(Long id); }
