package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.matching.party.dto.MemberArrived;
import team.codingforest.moyeota.matching.party.dto.MemberJoined;
import team.codingforest.moyeota.matching.party.dto.MemberLeft;
import team.codingforest.moyeota.matching.party.dto.PartyClosed;
import team.codingforest.moyeota.matching.party.dto.PartyConfirmed;
import team.codingforest.moyeota.matching.party.dto.RecruitingClosed;

public interface PartyChangeNotifier {
    void memberJoined(Long partyId, MemberJoined event);
    void memberLeft(Long partyId, Long memberId, MemberLeft event);
    void closed(Long partyId, PartyClosed event);
    void recruitingClosed(Long partyId, RecruitingClosed event);
    void confirmed(Long partyId, PartyConfirmed event);
    void memberArrived(Long partyId, MemberArrived event);
}
