package team.codingforest.moyeota.matching.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.matching.application.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.application.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.application.dto.PartyResult;
import team.codingforest.moyeota.matching.domain.*;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PartyApplicationService {

    private final Parties parties;

    @Transactional
    public PartyResult open(OpenPartyCommand command) {
        Party party = Party.open(command.hostId(),
                new Location(command.departureLat(), command.departureLng()),
                new Location(command.destinationLat(), command.destinationLng()),
                command.departure(), command.destination(), new Capacity(command.capacity()),
                Instant.now(), new Radius(command.departureRadius()), new Radius(command.destinationRadius()));

        PartyResult result = PartyResult.from(parties.save(party));

        log.info("매칭방 활성화. partyId={}, hostId={}, capacity={}", result.id(), result.hostId(), result.capacity());

        return result;
    }

    @Transactional
    public void join(Long partyId, Long memberId) {
        Party party = getParty(partyId);

        party.join(memberId);
        parties.save(party);

        log.info("매칭방에 사용자 참가됨 partyId={}, memberId={}, status={}", partyId, memberId, party.getStatus());
    }

    @Transactional
    public void leave(Long partyId, Long memberId) {
        Party party = getParty(partyId);

        party.leave(memberId);
        parties.save(party);

        log.info("매칭방에서 사용자 나감 partyId={}, memberId={}, status={}, members={}", partyId, memberId, party.getStatus(), party.getMembers().size());
    }

    @Transactional(readOnly = true)
    public PartyDetailResult getPartyDetail(Long partyId) {
        return PartyDetailResult.from(getParty(partyId));
    }


    // TODO 예외처리 해야함
    private Party getParty(Long partyId) {
        return parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 방이 없음"));
    }
}
