package team.codingforest.moyeota.matching.party.domain;

import java.time.Instant;
import java.util.List;

public interface ReservationReminderNotifier {
    void notifyDepartureSoon(Long partyId, List<Long> memberIds, Instant departureAt);
}
