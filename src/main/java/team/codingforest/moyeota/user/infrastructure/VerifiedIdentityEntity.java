package team.codingforest.moyeota.user.infrastructure;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.user.domain.VerifiedIdentity;
import java.time.Instant;

@Entity
@Table(name = "verified_identity")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerifiedIdentityEntity {
    @Id private Long userId;
    @Column(nullable = false, unique = true, length = 64) private String requestId;
    @Column(nullable = false, unique = true, length = 64) private String diHash;
    @Column(nullable = false) private Instant verifiedAt;
    @Column(nullable = false) private Instant completedAt;

    public VerifiedIdentityEntity(VerifiedIdentity identity, String diHash) {
        userId = identity.userId(); requestId = identity.requestId(); this.diHash = diHash;
        verifiedAt = identity.verifiedAt(); completedAt = identity.completedAt();
    }
    public VerifiedIdentity toDomain() { return new VerifiedIdentity(userId, requestId, verifiedAt, completedAt); }
}
