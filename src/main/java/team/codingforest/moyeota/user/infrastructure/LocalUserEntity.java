package team.codingforest.moyeota.user.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.user.domain.LocalUser;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocalUserEntity {
    @Id
    private Long id;

    @Column(nullable = false,  unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    private LocalUserEntity(Long userId, String loginId, String password) {
        this.id = userId;
        this.loginId = loginId;
        this.password = password;
        this.createdAt = Instant.now();
    }

    public static LocalUserEntity from(Long userId, String loginId, String password) {
        return new LocalUserEntity(userId, loginId, password);
    }

    public LocalUser toDomain() {
        return LocalUser.restore(getId(), loginId, password, getCreatedAt(), getUpdatedAt());
    }
}
