package team.codingforest.moyeota.matching.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.matching.application.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.application.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.application.dto.PartyResult;
import team.codingforest.moyeota.matching.domain.*;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PartyApplicationService {

    private final Parties parties;

    @Transactional
    public PartyResult open(OpenPartyCommand command) {
        validateNotInOngoingParty(command.hostId());
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
        validateNotInOngoingParty(memberId);
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

    @Transactional
    public void ready(Long partyId, Long memberId) {
        Party party = getParty(partyId);

        party.ready(memberId);
        parties.save(party);
        log.info("준비 완료 partyId={}, memberId={}", partyId, memberId);
    }

    @Transactional
    public void cancelReady(Long partyId, Long memberId) {
        Party party = getParty(partyId);

        party.cancelReady(memberId);
        parties.save(party);

        log.info("준비 취소 partyId={}, memberId={}", partyId, memberId);
    }

    @Transactional
    public void startMatching(Long partyId, Long memberId) {
        Party party = getParty(partyId);

        party.startMatching(memberId);
        parties.save(party);

        log.info("매칭 시작 partyId={}, hostId={}, status={}", partyId, memberId, party.getStatus());
    }
    @Transactional(readOnly = true)
    public List<PartyResult> findActiveParties() {
        return parties.findAllByStatus(PartyStatus.ACTIVE).stream()
                .map(PartyResult::from)
                .toList();
    }


    // TODO 예외처리 해야함
    private Party getParty(Long partyId) {
        return parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 방이 없음"));
    }

    private void validateNotInOngoingParty(Long memberId) {
        if(parties.existsOngoingByMemberId(memberId)) {
            throw new IllegalArgumentException("이미 참여 중인 방이 있습니다. memberId=" + memberId);
        }
    }
}
