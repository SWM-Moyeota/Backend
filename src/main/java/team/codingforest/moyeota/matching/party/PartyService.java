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
import team.codingforest.moyeota.matching.party.dto.MemberJoined;
import team.codingforest.moyeota.matching.party.dto.MemberLeft;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;
import team.codingforest.moyeota.matching.party.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.party.dto.PartyClosed;
import team.codingforest.moyeota.matching.party.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.party.dto.PartyResult;
import team.codingforest.moyeota.matching.party.dto.PartyStatusResponse;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.matching.party.dto.RealTimeMatchingCommand;
import team.codingforest.moyeota.matching.route.RouteService;
import team.codingforest.moyeota.matching.route.domain.Coordinate;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.route.domain.WalkRouteFinder;
import team.codingforest.moyeota.matching.route.domain.WalkingEstimate;
import team.codingforest.moyeota.matching.party.dto.MatchCandidate;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.record.domain.RideRecords;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class PartyService {
    /**
     * 지도 한 화면에 내려주는 방 수 상한. 넘으면 최신순으로 자른다
     */
    static final int MAP_LIST_LIMIT = 100;
    private static final int RADIUS = 300;

    private final Parties parties;
    private final ApplicationEventPublisher eventPublisher;
    private final RouteService routeService;
    private final DriverAccess driverAccess;
    private final UserAccess userAccess;
    private final PartyCompletionPolicy partyCompletionPolicy;
    private final AfterCommitExecutor afterCommitExecutor;
    private final PartyChangeNotifier partyChangeNotifier;
    private final WalkRouteFinder walkRouteFinder;
    private final RideRecords rideRecords;
	private final PartyOpener partyOpener;

    public PartyResult open(OpenPartyCommand command) {
        validateNotInOngoingParty(command.creatorId());

		// 네이버 API를 사용하기에 트랜잭션을 사용하지 않음
        RouteEstimate estimate = routeService.estimate(command.departureLat(), command.departureLng(), command.destinationLat(), command.destinationLng());

		// match 테이블 저장(트랜잭션)
		return partyOpener.open(command, estimate);
    }

    @Transactional
    public PartyStatusResponse join(Long partyId, Long memberId) {
        validateNotInOngoingParty(memberId);

        MemberSummary summary = userAccess.findMemberSummaries(List.of(memberId)).get(memberId);
        int rideCount = parties.countFinishedRides(List.of(memberId)).getOrDefault(memberId, 0);

        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        party.join(memberId);

        if (party.isFull()) {
            partyCompletionPolicy.onCompleted(party);
        }

        log.info("매칭방에 사용자 참가됨 partyId={}, memberId={}, status={}", partyId, memberId, party.getStatus());

        Party saved = parties.save(party);

        eventPublisher.publishEvent(new PartyMemberJoinedEvent(partyId, memberId));

        MemberJoined event = MemberJoined.from(PartyDetailResult.MemberInfo.of(summary, rideCount, saved.isHost(memberId)), saved.getStatus().name(), saved.getVersion());

        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.memberJoined(partyId, event));

        return PartyStatusResponse.from(saved);
    }

    @Transactional
    public void leave(Long partyId, Long memberId) {
        MemberSummary summary = userAccess.findMemberSummaries(List.of(memberId)).get(memberId);

        UUID publicId = summary == null ? null : summary.publicId();

        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        party.leave(memberId);
        parties.save(party);
        eventPublisher.publishEvent(new PartyMemberLeftEvent(partyId, memberId));

        if (party.getStatus() == PartyStatus.CANCELED) {
            eventPublisher.publishEvent(new PartyClosedEvent(partyId, party.getStatus().name()));
            PartyClosed closed = PartyClosed.from(party.getStatus().name());
            afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.closed(partyId, closed));
        } else {
            Long hostId = party.hostId();   // leave 반영 후 = 승계 결과. 이 분기에선 멤버가 반드시 남아 있음
            MemberSummary hostSummary = userAccess.findMemberSummaries(List.of(hostId)).get(hostId);
            UUID hostPublicId = hostSummary == null ? null : hostSummary.publicId();
            MemberLeft event = MemberLeft.from(publicId, party.getStatus().name(), hostPublicId, party.getVersion());
            afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.memberLeft(partyId, memberId, event));
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
        if (driverId == null) throw new BusinessException(MatchingErrorCode.DRIVER_NOT_ASSIGNED);

        return driverAccess.findSummary(driverId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.ASSIGNED_DRIVER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<PartySummary> findActivePartiesWithin(double swLat, double swLng, double neLat, double neLng) {
        if (swLat >= neLat || swLng >= neLng) throw new BusinessException(MatchingErrorCode.INVALID_MAP_BOUNDS);

        return parties.findSummariesWithinBounds(PartyStatus.ACTIVE, swLat, neLat, swLng, neLng, MAP_LIST_LIMIT);
    }

    @Transactional
    public void finish(Long partyId, Long memberId) {
        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        party.finishWithoutDriver(memberId);
        parties.save(party);
        rideRecords.save(RideRecord.withoutDriver(partyId, party.getDeparture(), party.getDestination()));
        eventPublisher.publishEvent(new PartyClosedEvent(partyId, party.getStatus().name()));
        PartyClosed closed = PartyClosed.from(party.getStatus().name());
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.closed(partyId, closed));

        log.info("기사 없이 합승 종료 partyId={}, memberId={}", partyId, memberId);
    }

    @Transactional
    public void expire(Long partyId) {
        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        party.expireCompleted();
        parties.save(party);
        eventPublisher.publishEvent(new PartyClosedEvent(partyId, party.getStatus().name()));
        PartyClosed closed = PartyClosed.from(party.getStatus().name());
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.closed(partyId, closed));

        log.warn("정원 충족 후 방치된 방 자동 종료 partyId={}", partyId);
    }

    public List<MatchCandidate> realtimeMatch(Long memberId, RealTimeMatchingCommand command) {
        Location departureLocation = new Location(command.departureLat(), command.departureLng());
        Location destinationLocation = new Location(command.destLat(), command.destLng());
        validateNotInOngoingParty(memberId);

        // 1. 입력으로 들어온 출발지 위경도와 목적지 위경도에서 300m 반경의 매칭방을 찾기
        List<NearbyPartySummary> summary = parties.findMatchesWithinRadius(departureLocation.latitude(), departureLocation.longitude(), destinationLocation.latitude(), destinationLocation.longitude(), RADIUS);

        // 2. OSRM으로 매칭방 목록과 현재 출발하고 싶은 출발점 위경도와 목적지 위경도 estimateTime 계산
        Coordinate departure = Coordinate.from(departureLocation.latitude(), departureLocation.longitude());
        Coordinate destination = Coordinate.from(destinationLocation.latitude(), destinationLocation.longitude());

        List<WalkingEstimate> toPickup = walkRouteFinder.fromTo(departure, summary.stream()
                .map(p -> new Coordinate(p.departureLat(), p.departureLng())).toList());

        List<WalkingEstimate> dropOff = walkRouteFinder.toFrom(destination, summary.stream()
                .map(p -> new Coordinate(p.destinationLat(), p.destinationLng())).toList());

        // 3. 출발지에서 인근 방으로 걸리는 시간 + 방의 목적지에서 원래 목적지로 걸리는 시간 정렬
        return IntStream.range(0, summary.size())
                .filter(i -> toPickup.get(i) != null && dropOff.get(i) != null)
                .mapToObj(i -> new MatchCandidate(summary.get(i), toPickup.get(i).durationSeconds() + dropOff.get(i).durationSeconds()))
                .sorted(Comparator.comparingInt(MatchCandidate::totalSeconds))
                .toList();
    }

    private Party getParty(Long partyId) {
        return parties.findById(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));
    }

    private void validateNotInOngoingParty(Long memberId) {
        if (parties.existsOngoingByMemberId(memberId)) {
            throw new BusinessException(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
        }
    }
}
