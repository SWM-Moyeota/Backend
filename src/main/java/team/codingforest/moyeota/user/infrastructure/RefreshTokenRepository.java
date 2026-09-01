package team.codingforest.moyeota.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.user.domain.RefreshToken;

import java.util.List;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    @Query("select r from RefreshTokenEntity r where r.publicId = :uuid")
    List<RefreshTokenEntity> findByPublicId(@Param("uuid") UUID uuid);
}
