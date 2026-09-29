package team.codingforest.moyeota.user.profile.domain;

import java.util.Optional;

public interface UserProfiles {
    void save(UserProfile userProfile);
    Optional<UserProfile> findByUserId(Long userId);
    boolean existsByPhoneNumber(String normalizedPhoneNumber);
}
