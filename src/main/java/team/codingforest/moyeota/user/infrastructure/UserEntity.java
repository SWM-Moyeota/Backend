package team.codingforest.moyeota.user.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(nullable = false)
    private UUID publicId;

    private String nickname;

    private String imageUrl;

    @Column(nullable = false)
    private LoginType loginType;

    private Long badgeId;

    private UserEntity(UUID publicId, String nickname, String imageUrl, LoginType loginType, Long badgeId) {
        this.publicId = publicId;
        this.nickname = nickname;
        this.imageUrl = imageUrl;
        this.loginType = loginType;
        this.badgeId = badgeId;
    }

    public static UserEntity from(User user) {
        return new UserEntity(user.getPublicId(), user.getNickname(), user.getImageUrl(), user.getLoginType(), user.getBadgeId());
    }

    public User toDomain() {
        return User.restore(
                getId(),
                publicId,
                nickname,
                imageUrl,
                loginType,
                badgeId,
                getUpdatedAt()
        );
    }

}
