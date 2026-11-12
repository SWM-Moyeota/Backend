package team.codingforest.moyeota.user.profile.domain;

import java.util.List;
import java.util.Optional;

public interface UserProfiles {
    void save(UserProfile userProfile);
    Optional<UserProfile> findByUserId(Long userId);
    Optional<UserProfile> findByPhoneNumber(String normalizedPhoneNumber);
    /** 프로필이 없는 userId(탈퇴 등)는 결과에서 빠진다 */
    List<UserProfile> findAllByUserIds(List<Long> userIds);
    boolean existsByPhoneNumber(String normalizedPhoneNumber);
    void deleteByUserId(Long userId);
}
