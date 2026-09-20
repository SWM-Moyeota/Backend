package team.codingforest.moyeota.user.infrastructure;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.user.domain.IdentityRequest;
import java.time.Instant;

@Entity
@Table(name = "identity_verification_request", indexes = @Index(name = "idx_identity_request_user_created", columnList = "user_id,created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityRequestEntity {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private String storeId;
    @Column(nullable = false) private String channelKey;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;

    public IdentityRequestEntity(IdentityRequest request) {
        id = request.id(); userId = request.userId(); storeId = request.storeId();
        channelKey = request.channelKey(); createdAt = request.createdAt(); expiresAt = request.expiresAt();
    }

    public IdentityRequest toDomain() {
        return new IdentityRequest(id, userId, storeId, channelKey, createdAt, expiresAt);
    }
}
