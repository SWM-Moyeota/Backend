package team.codingforest.moyeota.driver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.driver.domain.Drivers;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverRetentionSweeper {
    private final Drivers drivers;

    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "driver-retention-sweep", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void sweep() {
        int deleted = drivers.deleteWithdrawnBefore(Instant.now().minus(Drivers.RETENTION));
        log.info("보관 기간이 지난 탈퇴 기사 정보 삭제 count={}", deleted);
    }
}
