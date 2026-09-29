package team.codingforest.moyeota.matching.api;

import team.codingforest.moyeota.matching.api.dto.MatchingTarget;
import team.codingforest.moyeota.matching.api.dto.PartyChatSummary;
import team.codingforest.moyeota.matching.api.dto.PartySummary;

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
    boolean hasMemberOnParty(Long partyId, Long memberId);
    boolean isRidingMember(Long partyId, Long memberId);
    boolean isOnboardingMember(Long partyId, Long memberId);
    List<Long> findMemberIds(Long partyId);
}
