package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.matching.party.dto.MemberJoined;
import team.codingforest.moyeota.matching.party.dto.MemberLeft;
import team.codingforest.moyeota.matching.party.dto.PartyClosed;

public interface PartyChangeNotifier {
    void memberJoined(Long partyId, MemberJoined event);
    void memberLeft(Long partyId, Long memberId, MemberLeft event);
    void closed(Long partyId, PartyClosed event);
}
