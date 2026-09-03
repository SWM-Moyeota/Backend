package team.codingforest.moyeota.user.infrastructure;

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
}
