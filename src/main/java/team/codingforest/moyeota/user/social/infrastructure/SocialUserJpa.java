package team.codingforest.moyeota.user.social.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.social.domain.SocialUser;
import team.codingforest.moyeota.user.social.domain.SocialUsers;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SocialUserJpa implements SocialUsers {
    private final SocialUserRepository socialUserRepository;

    @Override
    public void register(Long userId, SocialType socialType, String socialId) {
        socialUserRepository.save(SocialUserEntity.from(userId, socialType, socialId));
    }

    @Override
    public Optional<SocialUser> find(SocialType socialType, String socialId) {
        return socialUserRepository.findBySocialTypeAndSocialId(socialType, socialId).map(SocialUserEntity::toDomain);
    }

    @Override
    public boolean exists(SocialType socialType, String socialId) {
        return socialUserRepository.existsBySocialTypeAndSocialId(socialType, socialId);
    }

    @Override
    public Optional<SocialUser> findByUserIdForUpdate(Long userId) {
        return socialUserRepository.findByIdForUpdate(userId).stream().findFirst().map(SocialUserEntity::toDomain);
    }

    @Override
    public void deleteByUserId(Long userId) {
        socialUserRepository.deleteAllByUserId(userId);
    }
}
