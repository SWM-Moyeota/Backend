package team.codingforest.moyeota.settlement.ledger.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Payouts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PayoutJpa implements Payouts {
    private final PayoutJpaRepository payoutJpaRepository;

    @Override
    @Transactional
    public Payout save(Payout payout) {
        if(payout.getId() == null) {
            return payoutJpaRepository.save(PayoutEntity.from(payout)).toDomain();
        }

        return payoutJpaRepository.findById(payout.getId())
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 지급입니다. payoutId=" + payout.getId()))
                .update(payout)
                .toDomain();
    }

    @Override
    public Optional<Payout> findById(Long id) {
        return payoutJpaRepository.findById(id)
                .map(PayoutEntity::toDomain);
    }

    @Override
    public Optional<Payout> findByIdForUpdate(Long id) {
        return payoutJpaRepository.findByIdForUpdate(id)
                .map(PayoutEntity::toDomain);
    }

    @Override
    public boolean existsActiveBySettlementId(Long settlementId) {
        return payoutJpaRepository.existsBySettlementIdAndStatusIn(settlementId, Payout.ACTIVE_STATUSES);
    }

    @Override
    public int countFailedBySettlementId(Long settlementId) {
        return Math.toIntExact(payoutJpaRepository.countBySettlementIdAndStatus(settlementId, PayoutStatus.FAILED));
    }

    @Override
    public Optional<Payout> findLatestBySettlementId(Long settlementId) {
        return payoutJpaRepository.findFirstBySettlementIdOrderByIdDesc(settlementId)
                .map(PayoutEntity::toDomain);
    }

    @Override
    public List<Long> findIdsToReconcile(Instant requestedBefore) {
        return payoutJpaRepository.findIdsToReconcile(PayoutStatus.REQUESTED, requestedBefore, PayoutStatus.UNKNOWN);
    }
}
