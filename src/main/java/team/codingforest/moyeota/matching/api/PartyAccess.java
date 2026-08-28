package team.codingforest.moyeota.matching.api;

import java.util.List;
import java.util.Optional;

public interface PartyAccess {
    Optional<PartySummary> findSummary(Long partyId);
    void assignDriver(Long partyId, Long driverId);
    void failMatching(Long partyId);
    List<MatchingTarget> findMatchingTargets();
    void startRide(Long partyId, Long driverId);
    void completeRide(Long partyId, Long driverId, int fare);
    boolean isAwaitingPickup(Long partyId, Long driverId);
    PartyChatSummary findChatSummary(Long partyId);
    boolean hasOngoingRide(Long driverId);
}
