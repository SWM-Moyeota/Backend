package team.codingforest.moyeota.user.deletion.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.deletion.domain.UserDeletions;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionSweeper {
    private final UserDeletions userDeletions;

    /**
     * 보관 기간이 지난 탈퇴 번호 기록을 지움
     * 개인정보처리방침에 적은 보관 기간(30일)과 실제 보관을 맞추려고 둠
     * 하루 한 번이라 기간을 넘겨 최대 하루 더 남을 수 있음
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "user-deletion-sweep", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void sweep() {
        int deleted = userDeletions.deleteRecordedBefore(Instant.now().minus(UserDeletions.RETENTION));
        log.info("보관 기간이 지난 탈퇴 번호 기록 삭제 count={}", deleted);
    }
}
