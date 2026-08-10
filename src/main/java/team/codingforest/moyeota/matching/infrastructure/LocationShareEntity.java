package team.codingforest.moyeota.matching.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.common.BaseEntity;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.matching.domain.LocationShare;

import java.time.Instant;

@Getter
@Entity
@Table(name = "location_share")
public class LocationShareEntity extends BaseEntity {
    @Column(nullable = false)
    private Long partyId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant stoppedAt;

    protected LocationShareEntity() {}

    private LocationShareEntity(Long partyId, Long memberId, Instant startedAt, Instant stoppedAt) {
        this.partyId = partyId;
        this.memberId = memberId;
        this.startedAt = startedAt;
        this.stoppedAt = stoppedAt;
    }

    public static LocationShareEntity from(LocationShare share) {
        return new LocationShareEntity(share.getPartyId(), share.getMemberId(), share.getStartedAt(), share.getStoppedAt());
    }

    public LocationShare toDomain() {
        return LocationShare.restore(getId(), partyId, memberId, startedAt, stoppedAt);
    }

    public void update(LocationShare share) {
        this.stoppedAt = share.getStoppedAt();
    }
}
