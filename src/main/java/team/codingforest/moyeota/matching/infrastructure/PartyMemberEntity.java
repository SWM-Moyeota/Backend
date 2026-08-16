package team.codingforest.moyeota.matching.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.matching.domain.PartyMember;
import team.codingforest.moyeota.matching.domain.enums.MemberStatus;

import java.time.Instant;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    protected PartyMemberEntity() {}

    private PartyMemberEntity(PartyEntity party, Long memberId, Instant joinedAt, MemberStatus status) {
        this.party = party;
        this.memberId = memberId;
        this.joinedAt = joinedAt;
        this.status = status;
    }

    public static PartyMemberEntity of(PartyEntity party, PartyMember member) {
        return new PartyMemberEntity(party, member.getMemberId(), member.getJoinedAt(), member.getStatus());
    }

    public PartyMember toDomain() {
        return PartyMember.restore(memberId, joinedAt, status);
    }
}
