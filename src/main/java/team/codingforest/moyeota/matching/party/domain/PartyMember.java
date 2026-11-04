package team.codingforest.moyeota.matching.party.domain;

import java.time.Instant;

/**
 *  방 멤버. 출석(도착·노쇼)은 애그리거트 루트 Party 를 거쳐서만 바뀐다 - 바꾸는 메소드는 패키지 안에서만 연다
 */
public class PartyMember {
    private final Long memberId;
    private final Instant joinedAt;
    // 약속 시간 뒤 출발지 100m 안에서 "도착했어요"를 누른 시각. 처음 누른 시각을 그대로 둔다
    private Instant arrivedAt;
    // 누군가 "출발했어요"를 누른 순간 도착하지 않았던 멤버에게 찍히는 시각
    private Instant noShowAt;
    // 운영자가 이의를 받아들여 노쇼를 무효로 돌린 시각. 무효가 된 노쇼는 노쇼가 아니다
    private Instant noShowVoidedAt;

    PartyMember(Long memberId, Instant joinedAt) {
        this(memberId, joinedAt, null, null, null);
    }

    private PartyMember(Long memberId, Instant joinedAt, Instant arrivedAt, Instant noShowAt, Instant noShowVoidedAt) {
        this.memberId = memberId;
        this.joinedAt = joinedAt;
        this.arrivedAt = arrivedAt;
        this.noShowAt = noShowAt;
        this.noShowVoidedAt = noShowVoidedAt;
    }

    public static PartyMember load(Long memberId, Instant joinedAt) {
        return new PartyMember(memberId, joinedAt);
    }

    public static PartyMember load(Long memberId, Instant joinedAt, Instant arrivedAt, Instant noShowAt, Instant noShowVoidedAt) {
        return new PartyMember(memberId, joinedAt, arrivedAt, noShowAt, noShowVoidedAt);
    }

    /** 이미 도착했으면 처음 시각을 그대로 둔다 */
    void arrive(Instant now) {
        if(arrivedAt == null) arrivedAt = now;
    }

    void markNoShow(Instant now) {
        noShowAt = now;
    }

    /**
     *  노쇼를 무효로 돌린다. 이미 무효였으면 처음 시각을 그대로 둔다
     *
     *  @return 이번에 무효로 돌렸으면 true
     */
    boolean voidNoShow(Instant now) {
        if(noShowVoidedAt != null) return false;

        noShowVoidedAt = now;
        return true;
    }

    public boolean isArrived() {
        return arrivedAt != null;
    }

    /** 노쇼로 찍혔고 운영자가 무효로 돌리지 않았다 - 탑승 기록·탑승 횟수에서 빠지는 기준 */
    public boolean isNoShow() {
        return noShowAt != null && noShowVoidedAt == null;
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

	public Instant getNoShowAt() {
		return noShowAt;
	}

	public Instant getNoShowVoidedAt() {
		return noShowVoidedAt;
	}
}
