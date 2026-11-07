package team.codingforest.moyeota.matching.party.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.party.domain.ReservationReminderNotifier;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class LoggingReservationReminderNotifier implements ReservationReminderNotifier {

    @Override
    public void notifyDepartureSoon(Long partyId, List<Long> memberIds, Instant departureAt) {
        log.info("[출발 전 알림] partyId={}, 구성원={}명, 출발={}", partyId, memberIds.size(), departureAt);
    }
}
