package team.codingforest.moyeota.matching.record.infrastructure;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

// 노쇼(no_show_at 이 있고 운영자가 무효로 돌리지 않은 멤버)는 타지 않은 사람이다 - 그 사람의 기록으로도, 다른 사람 기록의 동승자로도 나오지 않는다
public interface RideRecordJpaRepository extends JpaRepository<RideRecordEntity, Long> {

    @Query("""
            select h from RideRecordEntity h
            join PartyMemberEntity m on m.party.id = h.matchId
            where m.memberId = :memberId and h.dropoffTime is not null
                and (m.noShowAt is null or m.noShowVoidedAt is not null)
            order by h.dropoffTime desc, h.matchId desc
            """)
    List<RideRecordEntity> findDroppedOffByMember(@Param("memberId") Long memberId, Limit limit);

    @Query("""
            select h from RideRecordEntity h
            join PartyMemberEntity m on m.party.id = h.matchId
            where m.memberId = :memberId and h.dropoffTime is not null
                and (m.noShowAt is null or m.noShowVoidedAt is not null)
                and (h.dropoffTime < :dropoffTime or (h.dropoffTime = :dropoffTime and h.matchId < :matchId))
            order by h.dropoffTime desc, h.matchId desc
            """)
    List<RideRecordEntity> findDroppedOffByMemberBefore(@Param("memberId") Long memberId, @Param("dropoffTime") Instant dropoffTime,
                                                          @Param("matchId") Long matchId, Limit limit);

    @Query("""
            select m.party.id as matchId, m.memberId as memberId
            from PartyMemberEntity m
            where m.party.id in :matchIds
                and (m.noShowAt is null or m.noShowVoidedAt is not null)
            order by m.party.id, m.createdAt, m.memberId
            """)
    List<MatchMemberRow> findMembersByMatchIds(@Param("matchIds") Collection<Long> matchIds);
}
