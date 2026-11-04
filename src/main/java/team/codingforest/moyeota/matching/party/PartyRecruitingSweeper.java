package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.party.domain.Parties;

import java.time.Duration;
import java.time.Instant;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "false")
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyRecruitingSweeper {
    static final Duration REALTIME_RECRUITING_TTL = Duration.ofMinutes(15);
    static final Duration REMINDER_LEAD = Duration.ofMinutes(30);

    private final Parties parties;
    private final PartyService partyService;
    private final ReservationReminderService reminderService;

    @SchedulerLock(name = "party-recruiting-sweep", lockAtMostFor = "PT55S", lockAtLeastFor = "PT30S")
    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        Instant now = Instant.now();

        for(Long partyId : parties.findRecruitingExpired(now.minus(REALTIME_RECRUITING_TTL), now)) {
            try {
                partyService.expireRecruiting(partyId);
            } catch (BusinessException e) {
                log.info("모집 만료 건너 뜀 partyId={}, 사유={}", partyId, e.getMessage());
            }
        }
    }

    @SchedulerLock(name = "reservation-reminder", lockAtMostFor = "PT55S", lockAtLeastFor = "PT30S")
    @Scheduled(fixedDelay = 60_000)
    public void remind() {
        Instant now = Instant.now();

        for(Long partyId : parties.findReservationsToRemind(now, now.plus(REMINDER_LEAD))) {
            try {
                reminderService.remind(partyId);
            } catch (BusinessException e) {
                log.info("출발 전 알림 건너 뜀 partyId={}, 사유={}", partyId, e.getMessage());
            }
        }
    }
}
