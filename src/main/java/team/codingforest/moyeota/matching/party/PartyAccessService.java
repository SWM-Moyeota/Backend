package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartyChatSummary;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
class PartyAccessService implements PartyAccess {
    private final Parties parties;

    @Transactional(readOnly = true)
    @Override
    public PartyChatSummary findChatSummary(Long partyId) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        List<Long> members = party.getMembers().stream()
                .map(PartyMember::getMemberId).toList();

        return new PartyChatSummary(party.getId(), members, party.getDeparture(), party.getDestination());
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isOnboardingMember(Long partyId, Long memberId) {
        return parties.findById(partyId)
                .map(party -> party.hasMember(memberId) && party.getStatus().isOngoing())
                .orElse(false);
    }
}
