package team.codingforest.moyeota.matching.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartySummary;
import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.domain.Party;

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

    private PartySummary toSummary(Party party) {
        return new PartySummary(party.getId(), party.getDepartureLocation().latitude(), party.getDepartureLocation().longitude(),
                party.getDeparture(), party.getDestination());
    }
}
