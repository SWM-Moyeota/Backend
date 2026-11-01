package team.codingforest.moyeota.settlement.ledger.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SettlementLedgerJpaRepository extends JpaRepository<SettlementLedgerEntity, Long> {

    boolean existsByPaymentGroupId(Long paymentGroupId);

    @Query("select l from SettlementLedgerEntity l where l.settlementId = :settlementId order by l.occurredAt, l.id")
    List<SettlementLedgerEntity> findBySettlementId(@Param("settlementId") Long settlementId);

    @Query("""
            select l from SettlementLedgerEntity l
            where l.taxiDriverId = :taxiDriverId
                and l.settlementId is null
                and l.occurredAt >= :start and l.occurredAt < :end
            order by l.occurredAt, l.id
            """)
    List<SettlementLedgerEntity> findUnsettledByDriverIn(@Param("taxiDriverId") Long taxiDriverId, @Param("start") Instant start,
                                                         @Param("end") Instant end);

    @Query("""
            select distinct l.taxiDriverId from SettlementLedgerEntity l
            where l.settlementId is null
                and l.occurredAt < :cutoff
                and not exists (select s.id from SettlementEntity s
                                where s.taxiDriverId = l.taxiDriverId and s.periodStart = :periodStart)
            order by l.taxiDriverId
            """)
    List<Long> findDriverIdsToSettle(@Param("periodStart") Instant periodStart, @Param("cutoff") Instant cutoff);

    @Modifying(flushAutomatically = true)
    @Query("""
            update SettlementLedgerEntity l set l.settlementId = :settlementId
            where l.taxiDriverId = :taxiDriverId and l.settlementId is null and l.occurredAt < :cutoff
            """)
    int assignToSettlement(@Param("taxiDriverId") Long taxiDriverId, @Param("cutoff") Instant cutoff,
                           @Param("settlementId") Long settlementId);

    @Query("select sum(l.settlementAmount) from SettlementLedgerEntity l where l.settlementId = :settlementId")
    Long sumSettlementAmount(@Param("settlementId") Long settlementId);
}
