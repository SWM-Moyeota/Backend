package team.codingforest.moyeota.user.deletion.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
    @Table(name = "user_deletion")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDeletionEntity {

    @Id
    @Column(length = 64)
    private String phoneHash;

    @Column(nullable = false)
    private Instant deletedAt;

    private UserDeletionEntity(String phoneHash, Instant deletedAt) {
        this.phoneHash = phoneHash;
        this.deletedAt = deletedAt;
    }

    public static UserDeletionEntity of(String phoneHash, Instant deletedAt) {
        return new UserDeletionEntity(phoneHash, deletedAt);
    }
}
