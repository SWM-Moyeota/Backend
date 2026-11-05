package team.codingforest.moyeota.user.sanction.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseCreatedTimeEntity;
import team.codingforest.moyeota.user.sanction.domain.NoShow;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_no_show",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_no_show_user_match", columnNames = {"user_id", "match_id"}),
        indexes = @Index(name = "idx_user_no_show_user_meeting_at", columnList = "user_id, meeting_at"))
public class NoShowEntity extends BaseCreatedTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "match_id", nullable = false, updatable = false)
    private Long partyId;

    @Column(name = "meeting_at", nullable = false, updatable = false)
    private Instant meetingAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    private NoShowEntity(Long userId, Long partyId, Instant meetingAt) {
        this.userId = userId;
        this.partyId = partyId;
        this.meetingAt = meetingAt;
    }

    public static NoShowEntity of(Long userId, Long partyId, Instant meetingAt) {
        return new NoShowEntity(userId, partyId, meetingAt);
    }

    /** 이미 무효였으면 false - 처음 무효로 돌린 시각을 남긴다 */
    boolean markVoided(Instant voidedAt) {
        if(this.voidedAt != null) return false;

        this.voidedAt = voidedAt;
        return true;
    }

    public NoShow toDomain() {
        return new NoShow(meetingAt, voidedAt);
    }
}
