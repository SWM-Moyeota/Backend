package team.codingforest.moyeota.user.profile.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.common.crypto.FieldHasher;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserProfileJpa implements UserProfiles {
    private final UserProfileRepository userProfileRepository;
    private final FieldHasher hasher;

    @Override
    public void save(UserProfile user) {
        userProfileRepository.save(UserProfileEntity.from(user, hasher.hash(user.getPhoneNumber())));
    }

    @Override
    public java.util.Optional<UserProfile> findByUserId(Long userId) {
        return userProfileRepository.findById(userId).map(UserProfileEntity::toDomain);
    }

    @Override
    public List<UserProfile> findAllByUserIds(List<Long> userIds) {
        return userProfileRepository.findAllById(userIds).stream().map(UserProfileEntity::toDomain).toList();
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
