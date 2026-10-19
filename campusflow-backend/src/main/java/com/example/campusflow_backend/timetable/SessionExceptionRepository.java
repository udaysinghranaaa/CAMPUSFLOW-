package com.example.campusflow_backend.timetable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface SessionExceptionRepository extends JpaRepository<SessionException,Long>, JpaSpecificationExecutor<SessionException> { boolean existsBySessionId(Long id); Optional<SessionException> findBySessionIdAndExceptionDate(Long id, java.time.LocalDate date); }
