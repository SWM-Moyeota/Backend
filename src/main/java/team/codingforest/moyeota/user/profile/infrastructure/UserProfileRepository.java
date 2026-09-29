package team.codingforest.moyeota.user.profile.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Long> {
    boolean existsByPhoneNumber(String phoneNumber);
}
