package team.codingforest.moyeota.matching.domain;

import lombok.Getter;
import team.codingforest.moyeota.matching.domain.enums.MemberStatus;

import java.time.Instant;

@Getter
public class PartyMember {
    private final Long memberId;
    private final Instant joinedAt;
    private MemberStatus status;

    PartyMember(Long memberId, Instant joinedAt) {
        this.memberId = memberId;
        this.joinedAt = joinedAt;
        this.status = MemberStatus.NOT_READY;
    }

    PartyMember(Long memberId, Instant joinedAt, MemberStatus status) {
        this.memberId = memberId;
        this.joinedAt = joinedAt;
        this.status = status;
    }

    void ready() {
        this.status = MemberStatus.READY;
    }

    void cancelReady() {
        this.status = MemberStatus.NOT_READY;
    }

    public boolean isReady() {
        return status == MemberStatus.READY;
    }

    public static PartyMember restore(Long memberId, Instant joinedAt, MemberStatus status) {
        return new PartyMember(memberId, joinedAt, status);
    }
}
