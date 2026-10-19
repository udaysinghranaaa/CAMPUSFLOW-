package com.example.campusflow_backend.repository;

import com.example.campusflow_backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long> {
    Optional<RefreshToken> findByTokenHash(String hash);
    @Query("select t.userAccount.id from RefreshToken t where t.tokenHash=:hash")
    Optional<Long> findAccountIdByHash(@Param("hash") String hash);
    // Every mutation first locks the owning account, serializing all refresh/logout/password/status changes.
    @Modifying(flushAutomatically=true)
    @Query("update RefreshToken t set t.revokedAt=:now where t.userAccount.id=:id and t.revokedAt is null")
    int revokeAll(@Param("id") Long id,@Param("now") Instant now);
    void deleteByUserAccountId(Long id);
}
