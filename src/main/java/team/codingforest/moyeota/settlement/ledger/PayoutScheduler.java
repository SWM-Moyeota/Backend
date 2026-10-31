package team.codingforest.moyeota.settlement.ledger;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@ConditionalOnProperty(prefix = "moyeota.settlement.payout", name = "enabled", havingValue = "true")
@Component
@RequiredArgsConstructor
public class PayoutScheduler {
    private final PayoutService payoutService;

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "settlement-payout", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void pay() {
        payoutService.payDue();
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    @SchedulerLock(name = "settlement-payout-reconcile", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void reconcile() {
        payoutService.reconcile(Instant.now());
    }
}
