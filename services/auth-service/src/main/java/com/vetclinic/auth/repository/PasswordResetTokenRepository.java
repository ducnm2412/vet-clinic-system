package com.vetclinic.auth.repository;

import com.vetclinic.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Dùng để chặn spam: người này vừa yêu cầu cách đây chưa lâu. */
    boolean existsByUserIdAndCreatedAtAfter(UUID userId, Instant after);

    /** Huỷ mọi link còn sống của một người — mỗi lúc chỉ một link đặt lại có hiệu lực. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL")
    int invalidateAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
