package team.codingforest.moyeota.matching.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
@Table(name = "user_match_room")
public class PartyMemberEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private PartyEntity party;

    private Long memberId;

    private Instant joinedAt;
}
