package team.codingforest.moyeota.user.domain;

import lombok.Getter;
import team.codingforest.moyeota.user.domain.enums.LoginType;

import java.time.Instant;
import java.util.UUID;

@Getter
public class User {
    private final Long id;
    private final UUID publicId;
    private String nickname;
    private String imageUrl;
    private final LoginType loginType;
    private Long badgeId;
    private Instant updatedAt;

    private User(Long id, UUID publicId, String nickname, String imageUrl, LoginType loginType, Long badgeId, Instant updatedAt) {
        this.id = id;
        this.publicId = publicId;
        this.nickname = nickname;
        this.imageUrl = imageUrl;
        this.loginType = loginType;
        this.badgeId = badgeId;
        this.updatedAt = updatedAt;
    }

    public static User from(LoginType loginType) {
        return new User(null, UUID.randomUUID(), null, null, loginType, null, Instant.now());
    }

    public static User restore(Long id, UUID publicId, String nickname, String imageUrl, LoginType loginType, Long badgeId, Instant updatedAt) {
        return new User(id, publicId, nickname, imageUrl, loginType, badgeId, updatedAt);
    }
}
