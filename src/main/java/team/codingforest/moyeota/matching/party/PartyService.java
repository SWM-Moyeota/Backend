package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.driver.api.DriverSummary;
import team.codingforest.moyeota.matching.api.dto.PartyClosedEvent;
import team.codingforest.moyeota.matching.api.dto.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.api.dto.PartyMemberLeftEvent;
import team.codingforest.moyeota.matching.party.completion.PartyCompletionPolicy;
import team.codingforest.moyeota.matching.party.domain.PartyChangeNotifier;
import team.codingforest.moyeota.matching.party.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.party.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.party.dto.PartyResult;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.matching.route.RouteService;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PartyService {
    /** 지도 한 화면에 내려주는 방 수 상한. 넘으면 최신순으로 자른다 */
    static final int MAP_LIST_LIMIT = 100;

    private final Parties parties;
    private final ApplicationEventPublisher eventPublisher;
    private final RouteService routeService;
    private final DriverAccess driverAccess;
    private final UserAccess userAccess;
    private final PartyCompletionPolicy partyCompletionPolicy;
    private final AfterCommitExecutor afterCommitExecutor;
    private final PartyChangeNotifier partyChangeNotifier;

    @Transactional
    public PartyResult open(OpenPartyCommand command) {
        validateNotInOngoingParty(command.creatorId());

        RouteEstimate estimate = routeService.estimate(command.departureLat(), command.departureLng(), command.destinationLat(), command.destinationLng());

        Party party = Party.open(command.creatorId(),
                new Location(command.departureLat(), command.departureLng()),
                new Location(command.destinationLat(), command.destinationLng()),
                command.departure(), command.destination(), new Capacity(command.capacity()),
                Instant.now(), new Radius(command.departureRadius()), new Radius(command.destinationRadius()),
                estimate.estimateFare(), estimate.estimateTime(), estimate.path());

        Party saved = parties.save(party);

        if(saved.isFull()) {
            partyCompletionPolicy.onCompleted(saved);
            parties.save(saved);
            return PartyResult.from(saved);
        }

        eventPublisher.publishEvent(new PartyMemberJoinedEvent(saved.getId(), command.creatorId()));
        log.info("매칭방 활성화. partyId={}, creatorId={}, capacity={}, status={}", saved.getId(), command.creatorId(), saved.getCapacity().value(), saved.getStatus());

        return PartyResult.from(saved);
    }

    @Transactional
    public PartyDetailResult join(Long partyId, Long memberId) {
        validateNotInOngoingParty(memberId);
        Party party = parties.findByIdForUpdate(partyId)
                        .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        party.join(memberId);

        if(party.isFull()) {
            partyCompletionPolicy.onCompleted(party);
        }

        log.info("매칭방에 사용자 참가됨 partyId={}, memberId={}, status={}", partyId, memberId, party.getStatus());
        parties.save(party);
        eventPublisher.publishEvent(new PartyMemberJoinedEvent(partyId, memberId));
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.changed(partyId));

        return getPartyDetail(partyId);
    }

    @Transactional
    public void leave(Long partyId, Long memberId) {
        Party party = parties.findByIdForUpdate(partyId)
                        .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        party.leave(memberId);
        parties.save(party);
        eventPublisher.publishEvent(new PartyMemberLeftEvent(partyId, memberId));

        if(party.getStatus() == PartyStatus.CANCELED) {
            eventPublisher.publishEvent(new PartyClosedEvent(partyId, party.getStatus().name()));
            afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.closed(partyId));
        }
        else {
            afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.changed(partyId));
        }

        log.info("매칭방에서 사용자 나감 partyId={}, memberId={}, status={}, members={}", partyId, memberId, party.getStatus(), party.getMembers().size());
    }

    @Transactional(readOnly = true)
    public PartyDetailResult getPartyDetail(Long partyId) {
        Party party = getParty(partyId);

        List<Long> memberIds = party.getMembers().stream().map(PartyMember::getMemberId).toList();

        return PartyDetailResult.from(party, userAccess.findMemberSummaries(memberIds), parties.countFinishedRides(memberIds));
    }

    @Transactional(readOnly = true)
    public List<PartyResult> findActiveParties() {
        return parties.findAllByStatus(PartyStatus.ACTIVE).stream()
                .map(PartyResult::from)
                .toList();
    }


    @Transactional(readOnly = true)
    public DriverSummary getAssignDriver(Long partyId) {
        Party party = getParty(partyId);

        Long driverId = party.getTaxiDriverId();
        if(driverId == null) throw new BusinessException(MatchingErrorCode.DRIVER_NOT_ASSIGNED);

        return driverAccess.findSummary(driverId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.ASSIGNED_DRIVER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<PartySummary> findActivePartiesWithin(double swLat, double swLng, double neLat, double neLng) {
        if(swLat >= neLat || swLng >= neLng) throw new BusinessException(MatchingErrorCode.INVALID_MAP_BOUNDS);

        return parties.findSummariesWithinBounds(PartyStatus.ACTIVE, swLat, neLat, swLng, neLng, MAP_LIST_LIMIT);
    }

    @Transactional
    public void finish(Long partyId, Long memberId) {
        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        party.finishWithoutDriver(memberId);
        parties.save(party);
        eventPublisher.publishEvent(new PartyClosedEvent(partyId, party.getStatus().name()));
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.closed(partyId));

        log.info("기사 없이 합승 종료 partyId={}, memberId={}", partyId, memberId);
    }

    @Transactional
    public void expire(Long partyId) {
        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        party.expireCompleted();
        parties.save(party);
        eventPublisher.publishEvent(new PartyClosedEvent(partyId, party.getStatus().name()));
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.closed(partyId));

        log.warn("정원 충족 후 방치된 방 자동 종료 partyId={}", partyId);
    }


    private Party getParty(Long partyId) {
        return parties.findById(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));
    }

    private void validateNotInOngoingParty(Long memberId) {
        if(parties.existsOngoingByMemberId(memberId)) {
            throw new BusinessException(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
        }
    }

}
