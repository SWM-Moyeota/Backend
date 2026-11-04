package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.party.domain.Parties;

import java.time.Instant;

/**
 *  약속 시각 알림·출발 재촉 스케줄러(1차 모드 전용) - 1분마다 차례가 된 방을 골라 방마다 MeetingNotificationService 에 맡긴다.
 *  차례·방해 금지 시간 판단은 도메인과 서비스가 하고 여기서는 고르고 돌리기만 한다. 한 방이 실패해도 나머지 방은 계속 처리한다
 */
@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "false")
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingNotificationSweeper {
    private final Parties parties;
    private final MeetingNotificationService notificationService;

    @SchedulerLock(name = "meeting-notification-sweep", lockAtMostFor = "PT55S", lockAtLeastFor = "PT30S")
    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        Instant now = Instant.now();

        for(Long partyId : parties.findMeetingNoticeTargets(now)) {
            try {
                notificationService.notifyMeetingTime(partyId, now);
            } catch (RuntimeException e) {
                log.warn("약속 시각 알림 실패 partyId={}", partyId, e);
            }
        }

        for(Long partyId : parties.findDepartureReminderTargets(now)) {
            try {
                notificationService.remindDeparture(partyId, now);
            } catch (RuntimeException e) {
                log.warn("출발 재촉 실패 partyId={}", partyId, e);
            }
        }
    }
}
