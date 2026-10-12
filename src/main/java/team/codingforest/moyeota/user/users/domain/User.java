package team.codingforest.moyeota.user.users.domain;

import lombok.Getter;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;

import java.time.Instant;
import java.util.UUID;

/** publicId 는 밖(IdGenerator, UUID v7)에서 만들어 넘김. 토큰 sub 에만 쓰여 노출 적고, unique 인덱스 효율이 이득 */
@Getter
public class User {
    private final Long id;
    private final UUID publicId;
    private String nickname;
    private String imageUrl;
    private final LoginType loginType;
    private Long badgeId;
    private String fcmToken;
    private Instant updatedAt;
    private Instant deletedAt;

    private User(Long id, UUID publicId, String nickname, String imageUrl, LoginType loginType, Long badgeId, String fcmToken, Instant updatedAt, Instant deletedAt) {
        this.id = id;
        this.publicId = publicId;
        this.nickname = nickname;
        this.imageUrl = imageUrl;
        this.loginType = loginType;
        this.badgeId = badgeId;
        this.fcmToken = fcmToken;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    /** 소셜 가입용 - 닉네임은 첫 로그인 뒤 프로필 설정에서 채운다 */
    public static User from(UUID publicId, LoginType loginType) {
        return new User(null, publicId, null, null, loginType, null, null, Instant.now(), null);
    }

    /** 로컬 가입용 - 닉네임 필수 */
    public static User from(UUID publicId, LoginType loginType, String nickname) {
        return new User(null, publicId, new Nickname(nickname).value(), null, loginType, null, null, Instant.now(), null);
    }

    /** 프로필 편집 - 넘어온 값만 바꾼다 (null 은 유지) */
    public void updateProfile(String nickname, String imageUrl) {
        if(nickname != null) this.nickname = new Nickname(nickname).value();
        if(imageUrl != null) this.imageUrl = imageUrl;
        this.updatedAt = Instant.now();
    }

    public static User restore(Long id, UUID publicId, String nickname, String imageUrl, LoginType loginType, Long badgeId, String fcmToken, Instant updatedAt, Instant deletedAt) {
        return new User(id, publicId, nickname, imageUrl, loginType, badgeId, fcmToken, updatedAt, deletedAt);
    }

    /**
     *  FCM 토큰 관련
     */
    public void registerFcmToken(String token) {
        if(token == null || token.isBlank()) throw new UserException(UserErrorCode.EMPTY_FCM_TOKEN);

        this.fcmToken = token;
    }

    public boolean hasFcmToken() {
        return fcmToken != null;
    }

    public void clearFcmToken() {
        this.fcmToken = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void delete(Instant now) {
        if (isDeleted()) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }

        this.nickname = null;
        this.imageUrl = null;
        this.fcmToken = null;
        this.deletedAt = now;
        this.updatedAt = now;
    }
}
