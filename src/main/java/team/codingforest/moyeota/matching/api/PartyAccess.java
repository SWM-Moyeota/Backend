package team.codingforest.moyeota.matching.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PartyAccess {
    Optional<PartySummary> findSummary(Long partyId);
    void assignDriver(Long partyId, Long driverId);
    void cancelMatching(Long partyId);
    List<Long> findStaleMatchingIds(Instant cutoff);
}
