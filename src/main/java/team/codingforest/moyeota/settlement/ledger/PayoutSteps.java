package team.codingforest.moyeota.settlement.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutAccount;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutAccounts;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutOrder;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutResult;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Payouts;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Settlements;

import java.util.Optional;

@ConditionalOnProperty(prefix = "moyeota.settlement.payout", name = "enabled", havingValue = "true")
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutSteps {
    public static final int MAX_PAYOUT_ATTEMPTS = 3;

    private final Settlements settlements;
    private final Payouts payouts;
    private final PayoutAccounts payoutAccounts;

    @Transactional
    public Optional<PayoutOrder> prepare(Long settlementId) {
        Optional<Settlement> found = settlements.findByIdForUpdate(settlementId);
        if(found.isEmpty()) return Optional.empty();

        Settlement settlement = found.get();
        if(!canPayNow(settlement)) return Optional.empty();

        Optional<PayoutAccount> account = payoutAccounts.findByDriverId(settlement.getTaxiDriverId());
        if(account.isEmpty()) {
            hold(settlement);
            return Optional.empty();
        }

        return Optional.of(startPayout(settlement, account.get()));
    }

    @Transactional
    public void complete(Long payoutId, PayoutResult result) {
        Optional<Payout> found = payouts.findByIdForUpdate(payoutId);
        if(found.isEmpty()) {
            log.warn("결과를 반영할 지급이 없음 payoutId={}", payoutId);
            return;
        }

        Payout payout = found.get();
        if(payout.isFinished()) {
            log.warn("이미 끝난 지급이라 결과를 반영하지 않음 payoutId={}, payoutStatus={}, result={}", payoutId, payout.getStatus(), result);
            return;
        }

        Settlement settlement = settlements.findByIdForUpdate(payout.getSettlementId())
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));

        switch(result) {
            case PayoutResult.Succeeded succeeded -> {
                payout.succeed(succeeded.pgId());
                settlement.paid();
            }
            case PayoutResult.Failed failed -> {
                payout.fail(failed.reason());
                settlement.payoutFailed();
                log.warn("지급 실패 payoutId={}, settlementId={}, 사유={}", payoutId, settlement.getId(), failed.reason());
            }
            case PayoutResult.Unknown unknown -> {
                if(payout.getStatus() == PayoutStatus.REQUESTED) payout.markUnknown(unknown.reason());
                log.warn("지급 결과를 알 수 없음, 대사에서 확정 payoutId={}, settlementId={}, 사유={}", payoutId, settlement.getId(), unknown.reason());
            }
        }

        payouts.save(payout);
        settlements.save(settlement);
        log.info("지급 결과 반영 payoutId={}, settlementId={}, payoutStatus={}, settlementStatus={}",
                payoutId, settlement.getId(), payout.getStatus(), settlement.getStatus());
    }

    private boolean canPayNow(Settlement settlement) {
        if(!settlement.isPayable()) return false;
        if(payouts.existsActiveBySettlementId(settlement.getId())) return false;

        boolean usedUpAttempts = settlement.getStatus() == SettlementStatus.FAILED
                && payouts.countFailedBySettlementId(settlement.getId()) >= MAX_PAYOUT_ATTEMPTS;
        return !usedUpAttempts;
    }

    private void hold(Settlement settlement) {
        settlement.hold();
        settlements.save(settlement);
        log.warn("지급 계좌가 없어 정산서 지급 보류 settlementId={}, driverId={}", settlement.getId(), settlement.getTaxiDriverId());
    }

    private PayoutOrder startPayout(Settlement settlement, PayoutAccount account) {
        settlement.startPayout();
        Settlement started = settlements.save(settlement);
        Payout payout = payouts.save(Payout.request(started, account));
        log.info("지급 요청 기록 payoutId={}, settlementId={}, driverId={}, amount={}",
                payout.getId(), started.getId(), started.getTaxiDriverId(), payout.getAmount());

        return payout.toOrder();
    }
}
