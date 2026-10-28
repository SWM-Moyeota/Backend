package team.codingforest.moyeota.settlement.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;
import team.codingforest.moyeota.settlement.ledger.domain.Payouts;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedgers;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.Settlements;
import team.codingforest.moyeota.settlement.ledger.dto.SettlementDetailResponse;
import team.codingforest.moyeota.settlement.ledger.dto.WeeklySettlementResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {
    private final Settlements settlements;
    private final SettlementLedgers ledgers;
    private final Payouts payouts;

    @Transactional(readOnly = true)
    public WeeklySettlementResponse findWeekly(Long taxiDriverId, LocalDate date, Instant now) {
        SettlementPeriod week = date != null ? SettlementPeriod.weekOf(date) : SettlementPeriod.weekOf(now);

        Optional<Settlement> settlement = settlements.findByDriverAndPeriod(taxiDriverId, week);
        if(settlement.isPresent()) {
            return WeeklySettlementResponse.settled(settlement.get(), ledgers.findBySettlementId(settlement.get().getId()));
        }

        if(week.contains(now)) {
            List<SettlementLedger> unsettledThisWeek = ledgers.findUnsettledByDriver(taxiDriverId).stream()
                    .filter(ledger -> week.contains(ledger.getOccurredAt()))
                    .toList();
            return WeeklySettlementResponse.upcoming(week, unsettledThisWeek);
        }

        return WeeklySettlementResponse.empty(week);
    }

    @Transactional(readOnly = true)
    public SettlementDetailResponse findDetail(Long taxiDriverId, Long settlementId) {
        Settlement settlement = settlements.findById(settlementId)
                .filter(found -> found.isOwnedBy(taxiDriverId))
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));

        return SettlementDetailResponse.of(settlement, ledgers.findBySettlementId(settlement.getId()),
                payouts.findLatestBySettlementId(settlement.getId()).orElse(null));
    }
}
