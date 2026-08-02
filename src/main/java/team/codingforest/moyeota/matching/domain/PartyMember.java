package team.codingforest.moyeota.matching.domain;

import lombok.Getter;

import java.time.Instant;

@Getter
public class PartyMember {
    private final Long memberId;
    private final Instant joinedAt;

    PartyMember(Long memberId, Instant joinedAt) {
        this.memberId = memberId;
        this.joinedAt = joinedAt;
    }
}
