package team.codingforest.moyeota.user.social.domain;

import team.codingforest.moyeota.user.social.domain.enums.SocialType;

import java.util.Optional;

public interface SocialUsers {
    void register(Long userId, SocialType socialType, String socialId);
    /** 소셜 로그인의 출발점. socialId 만으로 찾지 않는다 - 서로 다른 소셜의 식별자가 겹칠 수 있다 */
    Optional<SocialUser> find(SocialType socialType, String socialId);
    boolean exists(SocialType socialType, String socialId);
    Optional<SocialUser> findByUserIdForUpdate(Long userId);
    void deleteByUserId(Long userId);
}
