package team.codingforest.moyeota.matching.party.domain;

import lombok.Getter;

import java.time.Instant;

public class PartyMember {
    private final Long memberId;
    private final Instant joinedAt;

    PartyMember(Long memberId, Instant joinedAt) {
        this.memberId = memberId;
        this.joinedAt = joinedAt;
    }

    public static PartyMember load(Long memberId, Instant joinedAt) {
        return new PartyMember(memberId, joinedAt);
    }

	public Long getMemberId() {
		return memberId;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}
}
