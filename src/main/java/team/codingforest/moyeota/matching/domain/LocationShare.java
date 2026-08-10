package team.codingforest.moyeota.matching.domain;

import lombok.Getter;

import java.time.Instant;

@Getter
public class LocationShare {
    private final Long id;
    private final Long partyId;
    private final Long memberId;
    private final Instant startedAt;
    private Instant stoppedAt;

    private LocationShare(Long id, Long partyId, Long memberId, Instant startedAt, Instant stoppedAt) {
        this.id = id;
        this.partyId = partyId;
        this.memberId = memberId;
        this.startedAt = startedAt;
        this.stoppedAt = stoppedAt;
    }

    public static LocationShare start(Long partyId, Long memberId, Instant startedAt) {
        return new LocationShare(null, partyId, memberId, startedAt, null);
    }

    public void stop(Instant now) {
        if(stoppedAt != null) throw new IllegalArgumentException("이미 종료된 공유임");
        this.stoppedAt = now;
    }

    public boolean isOngoing() {
        return stoppedAt == null;
    }

    public static LocationShare restore(Long id, Long partyId, Long memberId, Instant startedAt, Instant stoppedAt) {
        return new LocationShare(id, partyId, memberId, startedAt, stoppedAt);
    }
}
