package team.codingforest.moyeota.matching.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartySummary;
import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.domain.Party;

import java.time.Instant;
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
    public void cancelMatching(Long partyId) {
        Party party = getForUpdate(partyId);

        party.cancelMatching();

        parties.save(party);
    }

    @Override
    public List<Long> findMatchingIds() {
        return parties.findMatchingIds();
    }

    private Party getForUpdate(Long partyId) {
        return parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
    }

    private PartySummary toSummary(Party party) {
        return new PartySummary(party.getId(), party.getDepartureLocation().latitude(), party.getDepartureLocation().longitude(),
                party.getDestinationLocation().latitude(), party.getDestinationLocation().longitude(), party.getDeparture(), party.getDestination(),
                party.getMembers().size(), party.getEstimatedFare(), party.getEstimatedTime());
    }
}
