package team.codingforest.moyeota.user.profile.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.local.infrastructure.LocalUserRepository;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.common.crypto.FieldHasher;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserProfileJpa implements UserProfiles {
    private final UserProfileRepository userProfileRepository;
    private final FieldHasher hasher;
    private final LocalUserRepository localUsers;

    @Override
    @Transactional
    public void save(UserProfile user) {
        // 번호 변경은 재설정/탈퇴와 같은 순서로 잠그고 기존 인증을 무효화한다.
        localUsers.findByIdForUpdate(user.getUserId()).ifPresent(local ->
                userProfileRepository.findById(user.getUserId())
                        .filter(old -> !old.getPhoneHash().equals(hasher.hash(user.getPhoneNumber())))
                        .ifPresent(old -> local.invalidatePasswordReset()));
        userProfileRepository.save(UserProfileEntity.from(user, hasher.hash(user.getPhoneNumber())));
    }

    @Override
    public java.util.Optional<UserProfile> findByUserId(Long userId) {
        return userProfileRepository.findById(userId).map(UserProfileEntity::toDomain);
    }

    @Override
    public Optional<UserProfile> findByPhoneNumber(String phoneNumber) {
        return userProfileRepository.findByPhoneHash(hasher.hash(phoneNumber))
                .map(UserProfileEntity::toDomain);
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        return userProfileRepository.existsByPhoneHash(hasher.hash(phoneNumber));
    }

    @Override
    public void deleteByUserId(Long userId) {
        userProfileRepository.deleteById(userId);
    }
}
