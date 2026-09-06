package team.codingforest.moyeota.user.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.enums.LoginType;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
public class UserEntity extends BaseTimeEntity {

    // 필터가 매 요청 publicId 로 조회하므로 unique 인덱스 필요
    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId;

    private String nickname;

    private String imageUrl;

    // ordinal 이면 TINYINT 로 나가서 H2(PostgreSQL 모드) 테이블 생성 실패
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoginType loginType;

    private Long badgeId;

    private String fcmToken;

    private UserEntity(UUID publicId, String nickname, String imageUrl, LoginType loginType, Long badgeId, String fcmToken) {
        this.publicId = publicId;
        this.nickname = nickname;
        this.imageUrl = imageUrl;
        this.loginType = loginType;
        this.badgeId = badgeId;
        this.fcmToken = fcmToken;
    }

    public static UserEntity from(User user) {
        return new UserEntity(user.getPublicId(), user.getNickname(), user.getImageUrl(), user.getLoginType(), user.getBadgeId(), user.getFcmToken());
    }

    public void update(User user) {
        this.nickname = user.getNickname();
        this.imageUrl = user.getImageUrl();
        this.badgeId = user.getBadgeId();
        this.fcmToken = user.getFcmToken();
    }

    public User toDomain() {
        return User.restore(
                getId(),
                publicId,
                nickname,
                imageUrl,
                loginType,
                badgeId,
                fcmToken,
                getUpdatedAt()
        );
    }

}
