package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 *  약속 시각 알림·출발 재촉 스케줄러. 매분 0초에 돌아 [직전 분, 이번 분) 에 차례가 된 방만 고른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingNotificationSweeper {
	static final Duration TICK = Duration.ofMinutes(1);

	private final Parties parties;
	private final MeetingNotificationService notificationService;

	@SchedulerLock(name = "meeting-notification-sweep", lockAtMostFor = "PT55S", lockAtLeastFor = "PT30S")
	@Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
	public void sweep() {
		Instant now = Instant.now();
		Instant to = now.truncatedTo(ChronoUnit.MINUTES);
		Instant from = to.minus(TICK);

		for(Long partyId : parties.findOpenConfirmedIdsDepartingBetween(from, to)) {
			try {
				notificationService.notifyMeetingTime(partyId);
			} catch (RuntimeException e) {
				log.warn("약속 시각 알림 실패 partyId={}", partyId, e);
			}
		}

		// 재촉 차례 i 는 departure_at + OFFSETS[i] 가 구간 안인 방 - departure_at 으로 바꾸면 구간을 OFFSETS[i] 만큼 당긴 것
		for(Duration offset : Party.DEPARTURE_REMINDER_OFFSETS) {
			for(Long partyId : parties.findOpenConfirmedIdsDepartingBetween(from.minus(offset), to.minus(offset))) {
				try {
					notificationService.remindDeparture(partyId, now);
				} catch (RuntimeException e) {
					log.warn("출발 재촉 실패 partyId={}", partyId, e);
				}
			}
		}
	}
}