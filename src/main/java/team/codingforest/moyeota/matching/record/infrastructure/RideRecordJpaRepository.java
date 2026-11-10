package team.codingforest.moyeota.matching.record.infrastructure;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 탄 사람 기준 - 그 방에 도착한 멤버만 */
public interface RideRecordJpaRepository extends JpaRepository<RideRecordEntity, Long> {

    @Query("""
            select h from RideRecordEntity h
            join PartyMemberEntity m on m.party.id = h.matchId
            where m.memberId = :memberId
                and m.arrivedAt is not null
            order by h.pickupTime desc, h.matchId desc
            """)
    List<RideRecordEntity> findByMember(@Param("memberId") Long memberId, Limit limit);

    @Query("""
            select h from RideRecordEntity h
            join PartyMemberEntity m on m.party.id = h.matchId
            where m.memberId = :memberId
                and m.arrivedAt is not null
                and (h.pickupTime < :pickupTime or (h.pickupTime = :pickupTime and h.matchId < :matchId))
            order by h.pickupTime desc, h.matchId desc
            """)
    List<RideRecordEntity> findByMemberBefore(@Param("memberId") Long memberId, @Param("pickupTime") Instant pickupTime,
                                                          @Param("matchId") Long matchId, Limit limit);

    @Query("""
            select m.party.id as matchId, m.memberId as memberId
            from PartyMemberEntity m
            where m.party.id in :matchIds
                and m.arrivedAt is not null
            order by m.party.id, m.createdAt, m.memberId
            """)
    List<MatchMemberRow> findMembersByMatchIds(@Param("matchIds") Collection<Long> matchIds);
}
