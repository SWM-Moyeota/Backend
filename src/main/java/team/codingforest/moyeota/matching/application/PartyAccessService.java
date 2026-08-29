package team.codingforest.moyeota.matching.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.matching.api.MatchingTarget;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartyChatSummary;
import team.codingforest.moyeota.matching.api.PartySummary;
import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.PartyMember;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class PartyAccessService implements PartyAccess {
    private final Parties parties;

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
    }

    @Override
    public List<MatchingTarget> findMatchingTargets() {
        return parties.findMatchingTargets();
    }

    @Override
    public void startRide(Long partyId, Long driverId) {
        Party party = getForUpdate(partyId);

        party.startRide(driverId);
    }

    @Override
    public void completeRide(Long partyId, Long driverId, int fare) {
        Party party = getForUpdate(partyId);

        party.completeRide(driverId, fare);
    }

    @Override
    public boolean isAwaitingPickup(Long partyId, Long driverId) {
        return parties.findById(partyId)
                .map(party -> party.isAwaitingPickup(driverId))
                .orElse(false);
    }

    @Override
    public PartyChatSummary findChatSummary(Long partyId) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));

        if(!party.isFull()) throw new IllegalArgumentException("매칭할 수 없습니다.");

        List<Long> members = party.getMembers().stream()
                .map(PartyMember::getMemberId).toList();

        return new PartyChatSummary(party.getId(), members, party.getDeparture(), party.getDestination());
    }

    @Override
    public boolean hasOngoingRide(Long driverId) {
        return parties.hasOngoingRide(driverId);
    }

    @Override
    public boolean hasMemberOnParty(Long memberId, Long partyId) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));

        for(PartyMember partyMember : party.getMembers()) {
            if(partyMember.getMemberId().equals(memberId)) return true;
        }

        return false;
    }

    private Party getForUpdate(Long partyId) {
        return parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
    }

    private PartySummary toSummary(Party party) {
        return new PartySummary(party.getId(), party.getDepartureLocation().latitude(), party.getDepartureLocation().longitude(),
                party.getDestinationLocation().latitude(), party.getDestinationLocation().longitude(), party.getDeparture(), party.getDestination(),
                party.getMembers().size(), party.getEstimatedFare(), party.getEstimatedTime(), party.getTaxiDriverId());
    }
}
