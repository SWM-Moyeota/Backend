package team.codingforest.moyeota.user.social.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;

import java.util.Optional;

public interface SocialUserRepository extends JpaRepository<SocialUserEntity, Long> {
    Optional<SocialUserEntity> findBySocialTypeAndSocialId(SocialType socialType, String socialId);

    boolean existsBySocialTypeAndSocialId(SocialType socialType, String socialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SocialUserEntity s where s.userId = :userId")
    Optional<SocialUserEntity> findByIdForUpdate(@Param("userId") Long userId);
}
