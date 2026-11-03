package team.codingforest.moyeota.settlement.ledger.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedgers;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SettlementLedgerJpa implements SettlementLedgers {

    private final SettlementLedgerJpaRepository settlementLedgerJpaRepository;

    @Override
    public SettlementLedger save(SettlementLedger ledger) {
        if(ledger.getId() != null) {
            throw new IllegalStateException("정산 원장은 고칠 수 없습니다. ledgerId=" + ledger.getId());
        }

        return settlementLedgerJpaRepository.save(SettlementLedgerEntity.from(ledger)).toDomain();
    }

    @Override
    public boolean existsByPaymentGroupId(Long paymentGroupId) {
        return settlementLedgerJpaRepository.existsByPaymentGroupId(paymentGroupId);
    }

    @Override
    public List<SettlementLedger> findBySettlementId(Long settlementId) {
        return settlementLedgerJpaRepository.findBySettlementId(settlementId).stream()
                .map(SettlementLedgerEntity::toDomain).toList();
    }

    @Override
    public List<SettlementLedger> findUnsettledByDriverIn(Long taxiDriverId, SettlementPeriod week) {
        return settlementLedgerJpaRepository.findUnsettledByDriverIn(taxiDriverId, week.start(), week.end()).stream()
                .map(SettlementLedgerEntity::toDomain).toList();
    }

    @Override
    public List<Long> findDriverIdsToSettle(SettlementPeriod week) {
        return settlementLedgerJpaRepository.findDriverIdsToSettle(week.start(), week.end());
    }

    @Override
    @Transactional
    public int assignToSettlement(Long taxiDriverId, Instant cutoff, Long settlementId) {
        return settlementLedgerJpaRepository.assignToSettlement(taxiDriverId, cutoff, settlementId);
    }

    @Override
    public int sumSettlementAmount(Long settlementId) {
        Long sum = settlementLedgerJpaRepository.sumSettlementAmount(settlementId);
        return sum == null ? 0 : Math.toIntExact(sum);
    }
}
