package team.codingforest.moyeota.settlement.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;

import java.time.Instant;
import java.util.List;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementCloseScheduler {
    private final SettlementCloseService closeService;

	@Scheduled(cron = "0 5 0-9 * * MON", zone = "Asia/Seoul")
    @SchedulerLock(name = "settlement-close", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void close() {
        SettlementPeriod endedWeek = SettlementPeriod.weekOf(Instant.now()).previous();
        int created = 0;

        List<Long> driverIds = closeService.findDriversToClose(endedWeek, 0L);
        while(!driverIds.isEmpty()) {
            for(Long driverId : driverIds) {
                if(closeSafely(driverId, endedWeek)) created++;
            }
            driverIds = closeService.findDriversToClose(endedWeek, driverIds.getLast());
        }

        log.info("정산서 생성 배치 periodStart={}, count={}", endedWeek.startDate(), created);
    }

    private boolean closeSafely(Long driverId, SettlementPeriod week) {
        try {
            return closeService.close(driverId, week);
        } catch (RuntimeException e) {
            log.error("정산서 생성 실패 driverId={}, periodStart={}", driverId, week.startDate(), e);
            return false;
        }
    }
}
