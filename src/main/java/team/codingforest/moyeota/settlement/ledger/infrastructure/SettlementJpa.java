package team.codingforest.moyeota.settlement.ledger.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.Settlements;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SettlementJpa implements Settlements {
    private final SettlementJpaRepository settlementJpaRepository;

    @Override
    @Transactional
    public Settlement save(Settlement settlement) {
        if(settlement.getId() == null) {
            return settlementJpaRepository.save(SettlementEntity.from(settlement)).toDomain();
        }

        return settlementJpaRepository.findById(settlement.getId())
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND))
                .update(settlement)
                .toDomain();
    }

    @Override
    public Optional<Settlement> findById(Long id) {
        return settlementJpaRepository.findById(id)
                .map(SettlementEntity::toDomain);
    }

    @Override
    public Optional<Settlement> findByIdForUpdate(Long id) {
        return settlementJpaRepository.findByIdForUpdate(id)
                .map(SettlementEntity::toDomain);
    }

    @Override
    public Optional<Settlement> findByDriverAndPeriod(Long taxiDriverId, SettlementPeriod period) {
        return settlementJpaRepository.findByTaxiDriverIdAndPeriodStart(taxiDriverId, period.start())
                .map(SettlementEntity::toDomain);
    }

    @Override
    public List<Long> findUnpaidIds() {
        return settlementJpaRepository.findIdsByStatusIn(Settlement.UNPAID_STATUSES);
    }
}
