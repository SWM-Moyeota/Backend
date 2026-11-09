package team.codingforest.moyeota.matching.record.infrastructure;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 탄 사람 기준 - 도착자가 있는 방은 도착한 멤버만, 도착 기록이 없는 옛 방(출시 전 FINISHED)은 멤버 전원 */
public interface RideRecordJpaRepository extends JpaRepository<RideRecordEntity, Long> {

    @Query("""
            select h from RideRecordEntity h
            join PartyMemberEntity m on m.party.id = h.matchId
            where m.memberId = :memberId and h.dropoffTime is not null
                and (m.arrivedAt is not null or not exists (select a from PartyMemberEntity a where a.party = m.party and a.arrivedAt is not null))
            order by h.dropoffTime desc, h.matchId desc
            """)
    List<RideRecordEntity> findDroppedOffByMember(@Param("memberId") Long memberId, Limit limit);

    @Query("""
            select h from RideRecordEntity h
            join PartyMemberEntity m on m.party.id = h.matchId
            where m.memberId = :memberId and h.dropoffTime is not null
                and (m.arrivedAt is not null or not exists (select a from PartyMemberEntity a where a.party = m.party and a.arrivedAt is not null))
                and (h.dropoffTime < :dropoffTime or (h.dropoffTime = :dropoffTime and h.matchId < :matchId))
            order by h.dropoffTime desc, h.matchId desc
            """)
    List<RideRecordEntity> findDroppedOffByMemberBefore(@Param("memberId") Long memberId, @Param("dropoffTime") Instant dropoffTime,
                                                          @Param("matchId") Long matchId, Limit limit);

    @Query("""
            select m.party.id as matchId, m.memberId as memberId
            from PartyMemberEntity m
            where m.party.id in :matchIds
                and (m.arrivedAt is not null or not exists (select a from PartyMemberEntity a where a.party = m.party and a.arrivedAt is not null))
            order by m.party.id, m.createdAt, m.memberId
            """)
    List<MatchMemberRow> findMembersByMatchIds(@Param("matchIds") Collection<Long> matchIds);
}
