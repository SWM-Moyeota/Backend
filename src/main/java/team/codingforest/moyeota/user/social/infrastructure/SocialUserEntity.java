package team.codingforest.moyeota.user.social.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.user.social.domain.SocialUser;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;

import java.time.Instant;

@Entity
@Getter
@Table(name = "social_user", uniqueConstraints = { @UniqueConstraint(
        name = "uk_social_user_type_social_id", columnNames = {"social_type", "social_id"}),
        @UniqueConstraint(name = "uk_social_user_user_type", columnNames = {"user_id", "social_type"}) })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialUserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String socialId;

    // 본인인증 연계정보. 구글은 주지 않아 null
    private String socialCi;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public void activate() { status = "ACTIVE"; }
    public void revoke() { status = "REVOKED"; }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialType socialType;

    @Column(nullable = false)
    private Instant createdAt;

    private SocialUserEntity(Long userId, SocialType socialType, String socialId) {
        this.userId = userId;
        this.socialType = socialType;
        this.socialId = socialId;
        this.createdAt = Instant.now();
    }

    public static SocialUserEntity from(Long userId, SocialType socialType, String socialId) {
        return new SocialUserEntity(userId, socialType, socialId);
    }

    public SocialUser toDomain() {
        return SocialUser.restore(userId, socialType, socialId, socialCi, createdAt);
    }
}
