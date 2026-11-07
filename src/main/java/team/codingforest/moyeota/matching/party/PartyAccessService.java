package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartyChatSummary;
import team.codingforest.moyeota.matching.api.dto.PartyPlaces;
import team.codingforest.moyeota.matching.api.dto.PartyRiders;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Transactional(readOnly = true)
    @Override
    public Optional<PartyRiders> findRiders(Long partyId) {
        return parties.findById(partyId)
                .map(party -> new PartyRiders(party.getId(), party.getStatus() == PartyStatus.FINISHED,
                        party.getMembers().stream().map(PartyMember::getMemberId).toList(), party.riderIds()));
    }

    @Transactional(readOnly = true)
    @Override
    public Map<Long, PartyPlaces> findPlaces(Collection<Long> partyIds) {
        return parties.findPlaces(partyIds);
    }
}
