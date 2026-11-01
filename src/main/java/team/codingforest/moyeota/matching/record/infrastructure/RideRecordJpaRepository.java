package team.codingforest.moyeota.matching.record.infrastructure;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface RideRecordJpaRepository extends JpaRepository<RideRecordEntity, Long> {

    @Query("""
            select h from RideRecordEntity h
            join PartyMemberEntity m on m.party.id = h.matchId
            where m.memberId = :memberId and h.dropoffTime is not null
            order by h.dropoffTime desc, h.matchId desc
            """)
    List<RideRecordEntity> findDroppedOffByMember(@Param("memberId") Long memberId, Limit limit);

    @Query("""
            select h from RideRecordEntity h
            join PartyMemberEntity m on m.party.id = h.matchId
            where m.memberId = :memberId and h.dropoffTime is not null
                and (h.dropoffTime < :dropoffTime or (h.dropoffTime = :dropoffTime and h.matchId < :matchId))
            order by h.dropoffTime desc, h.matchId desc
            """)
    List<RideRecordEntity> findDroppedOffByMemberBefore(@Param("memberId") Long memberId, @Param("dropoffTime") Instant dropoffTime,
                                                          @Param("matchId") Long matchId, Limit limit);

    @Query("""
            select m.party.id as matchId, m.memberId as memberId
            from PartyMemberEntity m
            where m.party.id in :matchIds
            order by m.party.id, m.joinedAt, m.memberId
            """)
    List<MatchMemberRow> findMembersByMatchIds(@Param("matchIds") Collection<Long> matchIds);
}
