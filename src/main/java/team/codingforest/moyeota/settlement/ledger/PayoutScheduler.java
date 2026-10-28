package team.codingforest.moyeota.settlement.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@ConditionalOnProperty(prefix = "moyeota.settlement.payout", name = "enabled", havingValue = "true")
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutScheduler {
    private final PayoutService payoutService;

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "settlement-payout", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void pay() {
        int requested = payoutService.payDue();
        log.info("정산 지급 요청 count={}", requested);
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    @SchedulerLock(name = "settlement-payout-reconcile", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void reconcile() {
        int settled = payoutService.reconcile(Instant.now());
        if(settled > 0) log.info("지급 대사 확정 count={}", settled);
    }
}
