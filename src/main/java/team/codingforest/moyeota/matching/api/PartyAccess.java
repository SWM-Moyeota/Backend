package team.codingforest.moyeota.matching.api;

import team.codingforest.moyeota.matching.api.dto.PartyChatSummary;

public interface PartyAccess {
    PartyChatSummary findChatSummary(Long partyId);
    boolean isOnboardingMember(Long partyId, Long memberId);
}
