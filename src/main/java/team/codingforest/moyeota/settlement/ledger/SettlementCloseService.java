package team.codingforest.moyeota.settlement.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedgers;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.Settlements;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementCloseService {
    private final Settlements settlements;
    private final SettlementLedgers ledgers;

    @Transactional(readOnly = true)
    public List<Long> findDriversToClose(SettlementPeriod week) {
        return ledgers.findDriverIdsToSettle(week);
    }

    @Transactional
    public boolean close(Long taxiDriverId, SettlementPeriod week) {
        if(settlements.findByDriverAndPeriod(taxiDriverId, week).isPresent()) return false;

        Settlement settlement = settlements.save(Settlement.create(taxiDriverId, week));

        int assigned = ledgers.assignToSettlement(taxiDriverId, week.end(), settlement.getId());

        if(assigned == 0) {
            throw new IllegalStateException("정산서에 담을 미정산 원장이 없습니다. driverId=" + taxiDriverId + ", periodStart=" + week.startDate());
        }

        int ledgerTotal = ledgers.sumSettlementAmount(settlement.getId());
        settlement.fixAmount(ledgerTotal);
        settlements.save(settlement);

        log.info("정산서 생성 settlementId={}, driverId={}, periodStart={}, rideCount={}, amount={}",
                settlement.getId(), taxiDriverId, week.startDate(), assigned, settlement.getAmount());
        return true;
    }
}
