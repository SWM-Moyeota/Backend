package team.codingforest.moyeota.user.domain;

import lombok.Getter;

import java.time.Instant;

@Getter
public class LocalUser {
    private final Long userId;
    private final String loginId;
    private final String password;
    private final Instant createdAt;
    private Instant updatedAt;

    private LocalUser(Long userId, String loginId, String password, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.loginId = loginId;
        this.password = password;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static LocalUser register(String loginId, String password) {
        return new LocalUser(null, loginId, password, Instant.now(), Instant.now());
    }

    public static LocalUser from(Long userId, String loginId, String password, Instant createdAt, Instant updatedAt) {
        return new LocalUser(userId, loginId, password, createdAt, updatedAt);
    }

    public static LocalUser restore(Long userId, String loginId, String password, Instant createdAt, Instant updatedAt) {
        return new LocalUser(userId, loginId, password, createdAt, updatedAt);
    }
}
