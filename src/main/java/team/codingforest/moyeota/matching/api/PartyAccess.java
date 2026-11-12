package team.codingforest.moyeota.matching.api;

import team.codingforest.moyeota.matching.api.dto.PartyChatSummary;
import team.codingforest.moyeota.matching.api.dto.PartyPlaces;
import team.codingforest.moyeota.matching.api.dto.PartyRiders;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface PartyAccess {
    PartyChatSummary findChatSummary(Long partyId);
    boolean isOnboardingMember(Long partyId, Long memberId);

    Optional<PartyRiders> findRiders(Long partyId);

    Map<Long, PartyPlaces> findPlaces(Collection<Long> partyIds);
}
