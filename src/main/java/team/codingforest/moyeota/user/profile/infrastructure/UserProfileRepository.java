package team.codingforest.moyeota.user.profile.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Long> {
    boolean existsByPhoneHash(String phoneHash);
    Optional<UserProfileEntity> findByPhoneHash(String phoneHash);
}
