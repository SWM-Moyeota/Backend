package team.codingforest.moyeota.matching.noshow.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.matching.noshow.domain.NoShowHistory;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionStatus;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionSummary;

import java.util.List;
import java.util.Optional;

public interface NoShowObjectionJpaRepository extends JpaRepository<NoShowObjectionEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from NoShowObjectionEntity o where o.id = :id")
    Optional<NoShowObjectionEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUserIdAndPartyId(Long userId, Long partyId);

    @Query("""
            select o.id, o.userId, o.partyId, p.departure, p.destination, p.departureAt,
                   o.reason, o.status, o.createdAt, o.resolvedAt
            from NoShowObjectionEntity o
            join PartyEntity p on p.id = o.partyId
            where o.status = :status
            order by o.createdAt asc, o.id asc
            """)
    List<ObjectionSummary> findSummariesByStatus(@Param("status") ObjectionStatus status, Limit limit);

    // 노쇼로 찍힌 적 있는 멤버 행(무효로 돌아간 것 포함) + 그 방에 낸 이의(없으면 null)
    @Query("""
            select p.id, p.departure, p.destination, p.departureAt, m.noShowAt, m.noShowVoidedAt, o.status
            from PartyMemberEntity m
            join m.party p
            left join NoShowObjectionEntity o on o.partyId = p.id and o.userId = m.memberId
            where m.memberId = :userId and m.noShowAt is not null
            order by m.noShowAt desc, p.id desc
            """)
    List<NoShowHistory> findNoShowHistory(@Param("userId") Long userId, Limit limit);
}
