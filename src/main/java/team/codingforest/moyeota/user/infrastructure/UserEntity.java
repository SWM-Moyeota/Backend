package team.codingforest.moyeota.user.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.enums.LoginType;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "users")   // "user" 는 PostgreSQL·H2 예약어라 insert 자체가 실패한다
public class UserEntity extends BaseTimeEntity {

    // 필터가 매 요청 publicId 로 조회하므로 unique 인덱스 필요
    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId;

    // null 허용 - 소셜 가입 직후는 비어 있다. PostgreSQL/H2 모두 null 끼리는 unique 충돌 없음
    @Column(unique = true)
    private String nickname;

    private String imageUrl;

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
