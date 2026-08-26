package team.codingforest.moyeota.matching.application;

import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class PartyJpaTest implements Parties {
    private final Map<Long, Party> store = new HashMap<>();
    private Long sequence = 0L;

    @Override
    public Optional<Party> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Party save(Party party) {
        Long id = party.getId() != null ? party.getId() : ++sequence;
        Party saved = Party.restore(id, party.getHostId(),
                party.getDepartureLocation(), party.getDestinationLocation(),
                party.getDepartureRadius(), party.getDestinationRadius(),
                party.getDeparture(), party.getDestination(), party.getCapacity(),
                party.getMembers(), party.getCreatedAt(), party.getStatus(),
                party.getEstimatedFare(), party.getEstimatedTime(), party.getRoute(), party.getTaxiDriverId());

        store.put(id, saved);
        return saved;
    }

    @Override
    public List<Party> findAllByStatus(PartyStatus status) {
        return store.values().stream().filter(p -> p.getStatus() == status)
                .toList();
    }

    @Override
    public boolean existsOngoingByMemberId(Long memberId) {
        return store.values().stream()
                .anyMatch(p -> p.getStatus().isOngoing() && p.hasMember(memberId));
    }
}