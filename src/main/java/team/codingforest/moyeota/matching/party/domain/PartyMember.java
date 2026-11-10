package team.codingforest.moyeota.matching.party.domain;

import java.time.Instant;

/**
 *  방 멤버. 도착은 애그리거트 루트 Party 를 거쳐서만 바뀐다 - 바꾸는 메소드는 패키지 안에서만 연다.
 */
public class PartyMember {
    private final Long memberId;
    private final Instant joinedAt;
    private Instant arrivedAt;

    private PartyMember(Long memberId, Instant joinedAt, Instant arrivedAt) {
        this.memberId = memberId;
        this.joinedAt = joinedAt;
        this.arrivedAt = arrivedAt;
    }

    static PartyMember join(Long memberId, Instant now) {
        return new PartyMember(memberId, now, null);
    }

    public static PartyMember load(Long memberId, Instant joinedAt, Instant arrivedAt) {
        return new PartyMember(memberId, joinedAt, arrivedAt);
    }

    void arrive(Instant now) {
        if(arrivedAt == null) {
			arrivedAt = now;
		}
    }

    public boolean isArrived() {
        return arrivedAt != null;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }
}
