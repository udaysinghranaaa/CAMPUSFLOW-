package com.example.campusflow_backend.timetable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface AcademicTermRepository extends JpaRepository<AcademicTerm,Long>, JpaSpecificationExecutor<AcademicTerm> {  }
