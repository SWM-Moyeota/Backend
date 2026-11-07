package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.party.domain.Parties;

import java.time.Instant;

/**
 *  약속 시각 알림·출발 재촉 스케줄러 - 1분마다 차례가 된 방을 골라 방마다 알림 보냄(출발 30분전, 도착 요청 재촉)
 */
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
