package team.codingforest.moyeota.matching.noshow.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseCreatedTimeEntity;
import team.codingforest.moyeota.matching.noshow.domain.NoShowObjection;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionStatus;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "no_show_objection",
        uniqueConstraints = @UniqueConstraint(name = "uk_no_show_objection_user_match", columnNames = {"user_id", "match_id"}))
public class NoShowObjectionEntity extends BaseCreatedTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "match_id", nullable = false, updatable = false)
    private Long partyId;

    @Column(nullable = false, length = NoShowObjection.MAX_REASON_LENGTH, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ObjectionStatus status;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    private NoShowObjectionEntity(Long userId, Long partyId, String reason, ObjectionStatus status, Instant resolvedAt) {
        this.userId = userId;
        this.partyId = partyId;
        this.reason = reason;
        this.status = status;
        this.resolvedAt = resolvedAt;
    }

    public static NoShowObjectionEntity from(NoShowObjection objection) {
        return new NoShowObjectionEntity(objection.getUserId(), objection.getPartyId(), objection.getReason(),
                objection.getStatus(), objection.getResolvedAt());
    }

    /** 처리 결과만 옮긴다 - 신청자·방·사유는 바뀌지 않는다 */
    void update(NoShowObjection objection) {
        this.status = objection.getStatus();
        this.resolvedAt = objection.getResolvedAt();
    }

    public NoShowObjection toDomain() {
        return NoShowObjection.load(id, userId, partyId, reason, status, getCreatedAt(), resolvedAt);
    }
}
