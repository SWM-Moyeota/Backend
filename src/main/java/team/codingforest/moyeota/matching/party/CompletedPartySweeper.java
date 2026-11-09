package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.party.domain.Parties;

import java.time.Duration;
import java.time.Instant;

/**
 *  정리 스케줄러 - 실시간 방은 만든 지 1일, 예약 방은 출발 시각에서 1일이 지나도 열려 있는(ACTIVE·COMPLETED) 방을 1시간마다 닫는다.
 *  출발을 누르지 않고 흩어진 방이 열린 채 남으면 멤버가 새 방에 들어가지 못한다. 어떻게 닫고 누구를 노쇼로 찍을지는 Party.autoClose 가 정한다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompletedPartySweeper {
    static final Duration AUTO_CLOSE_AFTER = Duration.ofDays(1);

    private final Parties parties;
    private final PartyService partyService;

    @SchedulerLock(name = "completed-party-sweep", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
	@Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void sweep() {
        Instant cutoff = Instant.now().minus(AUTO_CLOSE_AFTER);

        for(Long partyId : parties.findAutoCloseTargets(cutoff)) {
            try {
                partyService.autoClose(partyId);
            } catch (BusinessException e) {
                log.info("자동 정리 건너 뜀 partyId={}, 사유={}", partyId, e.getMessage());
            }
        }
    }
}
