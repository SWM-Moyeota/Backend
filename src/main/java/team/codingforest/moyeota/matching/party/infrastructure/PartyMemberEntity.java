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
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.matching.party.domain.PartyMember;

import java.time.Instant;

@Entity
@Getter
@IdClass(PartyMemberId.class)
@Table(name = "user_match_room")
public class PartyMemberEntity extends BaseTimeEntity {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private PartyEntity party;

    @Id
    @Column(name = "user_id")
    private Long memberId;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    protected PartyMemberEntity() {}

    private PartyMemberEntity(PartyEntity party, Long memberId) {
        this.party = party;
        this.memberId = memberId;
    }

    public static PartyMemberEntity of(PartyEntity party, PartyMember member) {
        PartyMemberEntity entity = new PartyMemberEntity(party, member.getMemberId());
        entity.update(member);
        return entity;
    }

    /** 이미 있는 멤버 행의 도착을 옮긴다 - 합류 시각과 키는 바뀌지 않는다 */
    void update(PartyMember member) {
        this.arrivedAt = member.getArrivedAt();
    }

    public PartyMember toDomain() {
        return PartyMember.load(memberId, getCreatedAt(), arrivedAt);
    }
}
