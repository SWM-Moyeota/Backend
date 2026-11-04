package team.codingforest.moyeota.matching.party.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import team.codingforest.moyeota.matching.party.domain.PartyMember;

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

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "no_show_at")
    private Instant noShowAt;

    @Column(name = "no_show_voided_at")
    private Instant noShowVoidedAt;

    protected PartyMemberEntity() {}

    private PartyMemberEntity(PartyEntity party, Long memberId, Instant joinedAt) {
        this.party = party;
        this.memberId = memberId;
        this.joinedAt = joinedAt;
    }

    public static PartyMemberEntity of(PartyEntity party, PartyMember member) {
        PartyMemberEntity entity = new PartyMemberEntity(party, member.getMemberId(), member.getJoinedAt());
        entity.update(member);

        return entity;
    }

    /** 이미 있는 멤버 행의 출석(도착·노쇼) 변경을 옮긴다 - 합류 시각과 키는 바뀌지 않는다 */
    void update(PartyMember member) {
        this.arrivedAt = member.getArrivedAt();
        this.noShowAt = member.getNoShowAt();
        this.noShowVoidedAt = member.getNoShowVoidedAt();
    }

    public PartyMember toDomain() {
        return PartyMember.load(memberId, joinedAt, arrivedAt, noShowAt, noShowVoidedAt);
    }
}
