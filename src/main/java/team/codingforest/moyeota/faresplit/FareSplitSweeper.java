package team.codingforest.moyeota.faresplit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.faresplit.domain.FareSplits;

import java.time.Instant;

/**
 *  택시비 정산 스케줄러 - 1분마다 만료(시작 1일), 송금 재요청(마지막 요청 4시간 뒤), 입금 확인 요청(보냄 4시간 뒤)을 정산마다 처리한다.
 *  정산마다 서비스(프록시)를 거쳐 트랜잭션을 따로 열고, 한 정산이 실패해도 나머지를 계속한다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FareSplitSweeper {
    private final FareSplits fareSplits;
    private final FareSplitService fareSplitService;
    private final FareSplitNotificationService notificationService;

    @SchedulerLock(name = "fare-split-sweep", lockAtMostFor = "PT55S", lockAtLeastFor = "PT30S")
    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        sweepAt(Instant.now());
    }

    /** 한 번 돌 때의 시각 하나로 대상을 찾고 같은 시각을 정산마다 넘긴다. 만료를 먼저 해 닫힌 정산에 알림이 나가지 않게 한다 */
    void sweepAt(Instant now) {
        for(Long partyId : fareSplits.findExpireTargets(now)) {
            try {
                fareSplitService.expire(partyId, now);
            } catch (RuntimeException e) {
                log.warn("택시비 정산 만료 실패 partyId={}", partyId, e);
            }
        }

        for(Long partyId : fareSplits.findTransferRequestTargets(now)) {
            try {
                notificationService.remindTransfers(partyId, now);
            } catch (RuntimeException e) {
                log.warn("송금 재요청 실패 partyId={}", partyId, e);
            }
        }

        for(Long partyId : fareSplits.findConfirmRequestTargets(now)) {
            try {
                notificationService.requestConfirm(partyId, now);
            } catch (RuntimeException e) {
                log.warn("입금 확인 요청 실패 partyId={}", partyId, e);
            }
        }
    }
}
