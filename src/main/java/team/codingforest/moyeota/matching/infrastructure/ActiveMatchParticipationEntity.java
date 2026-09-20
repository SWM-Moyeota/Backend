package team.codingforest.moyeota.matching.infrastructure;

import jakarta.persistence.*;

/** 진행 중인 참여만 보관한다. 과거 참여 이력은 user_match_room에 유지한다. */
@Entity
@Table(name = "active_match_participation", indexes = @Index(name = "idx_active_match_party", columnList = "party_id"))
public class ActiveMatchParticipationEntity {
    @Id @Column(name = "user_id") private Long userId;
    @Column(name = "party_id", nullable = false) private Long partyId;
    protected ActiveMatchParticipationEntity() {}
}
