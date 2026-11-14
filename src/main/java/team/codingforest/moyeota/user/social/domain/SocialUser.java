package team.codingforest.moyeota.user.social.domain;

import lombok.Getter;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;

import java.time.Instant;

/** 소셜 로그인 수단. socialId 는 소셜 서비스가 주는 고정 식별자(구글은 sub) */
@Getter
public class SocialUser {
    private final Long userId;
    private final SocialType socialType;
    private final String socialId;
    private final String socialCi;
    private final Instant createdAt;

    private SocialUser(Long userId, SocialType socialType, String socialId, String socialCi, Instant createdAt) {
        this.userId = userId;
        this.socialType = socialType;
        this.socialId = socialId;
        this.socialCi = socialCi;
        this.createdAt = createdAt;
    }

    public static SocialUser restore(Long userId, SocialType socialType, String socialId, String socialCi, Instant createdAt) {
        return new SocialUser(userId, socialType, socialId, socialCi, createdAt);
    }
}
