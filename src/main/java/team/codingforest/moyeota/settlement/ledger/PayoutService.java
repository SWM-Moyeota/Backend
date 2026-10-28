package team.codingforest.moyeota.settlement.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutGateway;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutOrder;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutResult;
import team.codingforest.moyeota.settlement.ledger.domain.Payouts;
import team.codingforest.moyeota.settlement.ledger.domain.Settlements;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ConditionalOnProperty(prefix = "moyeota.settlement.payout", name = "enabled", havingValue = "true")
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {
    static final int BATCH_SIZE = 200;
    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    static final String NO_RESULT_REASON = "지급 요청 결과를 받지 못함";

    private final Settlements settlements;
    private final Payouts payouts;
    private final PayoutGateway gateway;
    private final PayoutSteps steps;

    public int payDue() {
        int requested = 0;

        List<Long> settlementIds = settlements.findPayableIds(PayoutSteps.MAX_PAYOUT_ATTEMPTS, 0L, BATCH_SIZE);
        while(!settlementIds.isEmpty()) {
            for(Long settlementId : settlementIds) {
                if(pay(settlementId)) requested++;
            }
            settlementIds = settlements.findPayableIds(PayoutSteps.MAX_PAYOUT_ATTEMPTS, settlementIds.getLast(), BATCH_SIZE);
        }
        return requested;
    }

    public int reconcile(Instant now) {
        Instant staleBefore = now.minus(STALE_AFTER);
        int settled = 0;

        List<Long> payoutIds = payouts.findStaleIds(staleBefore, 0L, BATCH_SIZE);
        while(!payoutIds.isEmpty()) {
            for(Long payoutId : payoutIds) {
                if(reconcileSafely(payoutId, staleBefore)) settled++;
            }
            payoutIds = payouts.findStaleIds(staleBefore, payoutIds.getLast(), BATCH_SIZE);
        }
        return settled;
    }

    private boolean pay(Long settlementId) {
        Optional<PayoutOrder> order;
        try {
            order = steps.prepare(settlementId);
        } catch (RuntimeException e) {
            log.error("지급 준비 실패 settlementId={}", settlementId, e);
            return false;
        }

        if(order.isEmpty()) return false;

        PayoutResult result = request(order.get());
        try {
            steps.complete(order.get().payoutId(), result);
        } catch (RuntimeException e) {
            log.error("지급 결과 반영 실패, 대사에서 확정 payoutId={}, settlementId={}, result={}",
                    order.get().payoutId(), settlementId, result, e);
        }
        return true;
    }

    private PayoutResult request(PayoutOrder order) {
        try {
            PayoutResult result = gateway.request(order);

            if(result == null) return new PayoutResult.Unknown(NO_RESULT_REASON);

            return result;
        } catch (RuntimeException e) {
            log.warn("결제사 지급 요청 응답을 받지 못함 payoutId={}", order.payoutId(), e);
            return new PayoutResult.Unknown(NO_RESULT_REASON + ": " + e.getClass().getSimpleName());
        }
    }

    private boolean reconcileSafely(Long payoutId, Instant staleBefore) {
        try {
            return reconcileOne(payoutId, staleBefore);
        } catch (RuntimeException e) {
            log.error("지급 대사 실패 payoutId={}", payoutId, e);
            return false;
        }
    }

    private boolean reconcileOne(Long payoutId, Instant staleBefore) {
        Optional<Payout> found = payouts.findById(payoutId);
        if(found.isEmpty() || found.get().isFinished()) return false;

        Payout payout = found.get();
        Optional<PayoutResult> recorded;
        try {
            recorded = gateway.find(payout.payoutKey());
        } catch (RuntimeException e) {
            log.warn("결제사 지급 조회 실패, 다음 대사에서 다시 확인 payoutId={}", payoutId, e);
            return false;
        }

        if(recorded.isPresent()) {
            steps.complete(payoutId, recorded.get());
            return !(recorded.get() instanceof PayoutResult.Unknown);
        }

        boolean waitedLongEnough = payout.getCreatedAt().isBefore(staleBefore);
        if(!waitedLongEnough) return false;

        PayoutResult resent = request(payout.toOrder());

        steps.complete(payoutId, resent);

        log.info("결제사에 기록이 없는 지급을 같은 지급 키로 다시 요청 payoutId={}, result={}", payoutId, resent);

        return !(resent instanceof PayoutResult.Unknown);
    }
}
