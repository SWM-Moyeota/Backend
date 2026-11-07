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

    /** 방이 없으면 empty. 잠그지 않고 읽는다 */
    Optional<PartyRiders> findRiders(Long partyId);

    /** 방 id → 출발지·도착지 이름. 없는 방은 빠진다 */
    Map<Long, PartyPlaces> findPlaces(Collection<Long> partyIds);
}
