package com.example.campusflow_backend.repository;

import com.example.campusflow_backend.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount,Long> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByRole(AccountRole role);
    boolean existsByStudentId(Long id);
    boolean existsByFacultyId(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> lockById(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.email = :email")
    Optional<UserAccount> lockByEmail(@Param("email") String email);
}
