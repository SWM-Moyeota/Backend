package team.codingforest.moyeota.matching.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.matching.domain.PartyMember;

import java.time.Instant;

// 생성시간, 변경 시간 컬럼이 없음
@Entity
@Getter
@IdClass(PartyMemberId.class)
@Table(name = "user_match_room")
public class PartyMemberEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private PartyEntity party;

    @Id
    @Column(name = "user_id")
    private Long memberId;

    @Column(nullable = false)
    private Instant joinedAt;

    protected PartyMemberEntity() {}

    private PartyMemberEntity(PartyEntity party, Long memberId, Instant joinedAt) {
        this.party = party;
        this.memberId = memberId;
        this.joinedAt = joinedAt;
    }

    public static PartyMemberEntity of(PartyEntity party, PartyMember member) {
        return new PartyMemberEntity(party, member.getMemberId(), member.getJoinedAt());
    }

    public PartyMember toDomain() {
        return PartyMember.restore(memberId, joinedAt);
    }
}
