package team.codingforest.moyeota.user.social;

import team.codingforest.moyeota.user.social.domain.SocialUser;
import team.codingforest.moyeota.user.social.domain.SocialUsers;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 *  인메모리 SocialUsers. 실제 테이블처럼 (socialType, socialId) 가 겹치면 저장을 거부한다
 */
public class FakeSocialUsers implements SocialUsers {
    private final Map<Long, SocialUser> byUserId = new HashMap<>();

    @Override
    public void register(Long userId, SocialType socialType, String socialId) {
        if (exists(socialType, socialId)) {
            throw new IllegalStateException("uk_social_user_type_social_id 위반");
        }
        byUserId.put(userId, SocialUser.restore(userId, socialType, socialId, null, Instant.now()));
    }

    @Override
    public Optional<SocialUser> find(SocialType socialType, String socialId) {
        return byUserId.values().stream()
                .filter(u -> u.getSocialType() == socialType && u.getSocialId().equals(socialId))
                .findFirst();
    }

    @Override
    public boolean exists(SocialType socialType, String socialId) {
        return find(socialType, socialId).isPresent();
    }

    @Override
    public Optional<SocialUser> findByUserIdForUpdate(Long userId) {
        return Optional.ofNullable(byUserId.get(userId));
    }

    @Override
    public void deleteByUserId(Long userId) {
        byUserId.remove(userId);
    }
}
