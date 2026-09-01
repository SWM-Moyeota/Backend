package team.codingforest.moyeota.matching.application;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.driver.api.DriverSummary;
import team.codingforest.moyeota.matching.application.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.application.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.application.dto.PartyResult;
import team.codingforest.moyeota.matching.api.MatchingStartedEvent;
import team.codingforest.moyeota.matching.domain.*;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PartyApplicationService {

    private final Parties parties;
    private final ApplicationEventPublisher eventPublisher;
    private final RouteFinder routefinder;
    private final RouteCache routeCache;
    private final DriverAccess driverAccess;

    @Transactional
    public PartyResult open(OpenPartyCommand command) {
        validateNotInOngoingParty(command.creatorId());

        RouteEstimate estimate = estimateRoute(command.departureLat(), command.departureLng(), command.destinationLat(), command.destinationLng());

        Party party = Party.open(command.creatorId(),
                new Location(command.departureLat(), command.departureLng()),
                new Location(command.destinationLat(), command.destinationLng()),
                command.departure(), command.destination(), new Capacity(command.capacity()),
                Instant.now(), new Radius(command.departureRadius()), new Radius(command.destinationRadius()),
                estimate.estimateFare(), estimate.estimateTime(), estimate.path());

        Party saved = parties.save(party);

        // 최대 정원이 1명인 방은 바로 매칭 시작
        if(saved.isFull()) {
            saved.startMatching(Instant.now());
            parties.save(saved);
            eventPublisher.publishEvent(new MatchingStartedEvent(saved.getId()));

            log.info("매칭 시작 partyId={}, status={}", saved.getId(), saved.getStatus());
        }

        PartyResult result = PartyResult.from(saved);

        log.info("매칭방 활성화. partyId={}, creatorId={}, capacity={}, status={}", result.id(), command.creatorId(), result.capacity(), result.status());

        return result;
    }

    @Transactional
    public PartyDetailResult join(Long partyId, Long memberId) {
        validateNotInOngoingParty(memberId);
        Party party = getParty(partyId);

        party.join(memberId);

        if(party.isFull()) {
            party.startMatching(Instant.now());
            eventPublisher.publishEvent(new MatchingStartedEvent(partyId));
            log.info("매칭시작 partyId={}, status={}", party.getId(), party.getStatus());
        }

        log.info("매칭방에 사용자 참가됨 partyId={}, memberId={}, status={}", partyId, memberId, party.getStatus());
        parties.save(party);

        return getPartyDetail(partyId);
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

    @Transactional(readOnly = true)
    public List<PartyResult> findActiveParties() {
        return parties.findAllByStatus(PartyStatus.ACTIVE).stream()
                .map(PartyResult::from)
                .toList();
    }

    public RouteEstimate previewRoute(double departureLat, double departureLng, double destinationLat, double destinationLng) {
        return estimateRoute(departureLat, departureLng, destinationLat, destinationLng);
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
    public List<PartyResult> findActivePartiesWithin(double swLat, double swLng, double neLat, double neLng) {
        if(swLat >= neLat || swLng >= neLng) throw new BusinessException(MatchingErrorCode.INVALID_MAP_BOUNDS);

        return parties.findAllByStatusWithinBounds(PartyStatus.ACTIVE, swLat, neLat, swLng, neLng)
                .stream().map(PartyResult::from)
                .toList();
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

    // 출발지 위도, 경도, 도착지 위도 경도, 최단 거리 캐싱
    private RouteEstimate estimateRoute(double departureLat, double departureLng, double destinationLat, double destinationLng) {
        RouteKey key = RouteKey.of(departureLat, departureLng, destinationLat, destinationLng);

        return routeCache.find(key).orElseGet(() -> {
            RouteEstimate estimate = routefinder.find(key);
            routeCache.save(key, estimate);
            log.info("경로 산출(네이버) key={}, fare={}, minutes={}", key.value(), estimate.estimateFare(), estimate.estimateTime());
            return estimate;
        });
    }
}
