package team.codingforest.moyeota.settlement.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.api.SettlementAccess;
import team.codingforest.moyeota.settlement.api.dto.RideSettlementCommand;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedgers;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementLedgerService implements SettlementAccess {
    private final SettlementLedgers ledgers;

    @Override
    @Transactional
    public void record(RideSettlementCommand command) {
        if(command == null) throw new BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_COMMAND);

        SettlementLedger ledger = SettlementLedger.record(command.taxiDriverId(), command.partyId(), command.totalFare(),
                command.platformPromotion(), command.occurredAt());

        if(ledgers.existsByMatchId(ledger.getMatchId())) {
            log.info("이미 정산 원장에 기록된 운행이라 건너뜀 partyId={}", ledger.getMatchId());
            return;
        }

        ledgers.save(ledger);
        log.info("미정산 원장 기록 partyId={}, driverId={}, amount={}, occurredAt={}",
                ledger.getMatchId(), ledger.getTaxiDriverId(), ledger.getSettlementAmount(), ledger.getOccurredAt());
    }
}
