package team.codingforest.moyeota.user.auth.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    @Query("select r from RefreshTokenEntity r where r.jti = :jti")
    Optional<RefreshTokenEntity> findByJti(@Param("jti") UUID jti);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshTokenEntity r SET r.cancelledAt = :now, r.updatedAt = :now WHERE r.jti = :jti AND r.cancelledAt IS NULL AND r.expiresAt > :now")
    int consume(@Param("jti") UUID jti, @Param("now") Instant now);

    // 같은 트랜잭션에서 바꾼 다른 엔티티(탈퇴 처리)를 먼저 내보내야 clear 로 사라지지 않는다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenEntity r SET r.cancelledAt = :now, r.updatedAt = :now WHERE r.userId = :userId AND r.cancelledAt IS NULL")
    int cancelAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
