package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.matching.api.dto.MatchingTarget;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartyChatSummary;
import team.codingforest.moyeota.matching.api.dto.PartySummary;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyChangeNotifier;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.dto.PartyClosed;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class PartyAccessService implements PartyAccess {
    private final Parties parties;
    private final AfterCommitExecutor afterCommitExecutor;
    private final PartyChangeNotifier partyChangeNotifier;

    @Transactional(readOnly = true)
    @Override
    public Optional<PartySummary> findSummary(Long partyId) {
        return parties.findById(partyId)
                .map(this::toSummary);
    }

    @Transactional
    @Override
    public void assignDriver(Long partyId, Long driverId) {
        Party party = getForUpdate(partyId);

        party.assignDriver(driverId);

        parties.save(party);
    }

    @Transactional
    @Override
    public void failMatching(Long partyId) {
        Party party = getForUpdate(partyId);

        party.failMatching();

        parties.save(party);
        notifyClosed(party);
    }

    @Transactional(readOnly = true)
    @Override
    public List<MatchingTarget> findMatchingTargets() {
        return parties.findMatchingTargets();
    }

    @Transactional
    @Override
    public void startRide(Long partyId, Long driverId) {
        Party party = getForUpdate(partyId);

        party.startRide(driverId);
        parties.save(party);
    }

    @Transactional
    @Override
    public void completeRide(Long partyId, Long driverId, int fare) {
        Party party = getForUpdate(partyId);

        party.completeRide(driverId, fare);
        parties.save(party);
        notifyClosed(party);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isAwaitingPickup(Long partyId, Long driverId) {
        return parties.findById(partyId)
                .map(party -> party.isAwaitingPickup(driverId))
                .orElse(false);
    }

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
    public boolean hasOngoingRide(Long driverId) {
        return parties.hasOngoingRide(driverId);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasMemberOnParty(Long partyId, Long memberId) {
        if(!parties.existsById(partyId)) {
            throw new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND);
        }

        return parties.hasMember(partyId, memberId);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isRidingMember(Long partyId, Long memberId) {
        return parties.findById(partyId)
                .map(party -> party.hasMember(memberId) && party.getStatus().isRiding())
                .orElse(false);
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
    public List<Long> findMemberIds(Long partyId) {
        return parties.findById(partyId)
                .map(party -> party.getMembers().stream().map(PartyMember::getMemberId).toList())
                .orElse(List.of());
    }

    private void notifyClosed(Party party) {
        PartyClosed closed = PartyClosed.from(party.getId(), party.getStatus().name(), party.getVersion());
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.closed(party.getId(), closed));
    }

    private Party getForUpdate(Long partyId) {
        return parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));
    }

    private PartySummary toSummary(Party party) {
        return new PartySummary(party.getId(), party.getDepartureLocation().latitude(), party.getDepartureLocation().longitude(),
                party.getDestinationLocation().latitude(), party.getDestinationLocation().longitude(), party.getDeparture(), party.getDestination(),
                party.getMembers().size(), party.getEstimatedFare(), party.getEstimatedTime(), party.getTaxiDriverId());
    }
}
