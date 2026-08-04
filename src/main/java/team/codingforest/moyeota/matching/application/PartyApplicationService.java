package team.codingforest.moyeota.matching.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.matching.domain.*;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PartyApplicationService {

    private final Parties parties;

    @Transactional
    public Party open(OpenPartyCommand command) {
        Party party = Party.open(command.hostId(),
                new Location(command.departureLat(), command.departureLng()),
                new Location(command.destinationLat(), command.destinationLng()),
                command.departure(), command.destination(), new Capacity(command.capacity()),
                Instant.now(), new Radius(command.departureRadius()), new Radius(command.destinationRadius()));

        return parties.save(party);
    }

    @Transactional
    public void join(Long partyId, Long memberid) {

    }
}
