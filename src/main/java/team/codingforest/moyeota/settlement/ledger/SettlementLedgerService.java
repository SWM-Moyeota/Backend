package team.codingforest.moyeota.settlement.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.settlement.api.SettlementAccess;
import team.codingforest.moyeota.settlement.api.dto.PaymentGroupSettlementCommand;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedgers;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementLedgerService implements SettlementAccess {
    private final SettlementLedgers ledgers;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(PaymentGroupSettlementCommand command) {
        SettlementLedger ledger = SettlementLedger.record(command.taxiDriverId(), command.paymentGroupId(), command.totalFare(),
                command.platformPromotion(), command.paymentGroupCreatedAt());

        if(ledgers.existsByPaymentGroupId(ledger.getPaymentGroupId())) {
            log.warn("이미 정산 원장에 기록된 결제 그룹이 다시 와서 처음 기록을 그대로 둠 paymentGroupId={}, driverId={}, amount={}, occurredAt={}",
                    ledger.getPaymentGroupId(), ledger.getTaxiDriverId(), ledger.getSettlementAmount(), ledger.getOccurredAt());
            return;
        }

        ledgers.save(ledger);
        log.info("미정산 원장 기록 paymentGroupId={}, driverId={}, amount={}, occurredAt={}",
                ledger.getPaymentGroupId(), ledger.getTaxiDriverId(), ledger.getSettlementAmount(), ledger.getOccurredAt());
    }
}
