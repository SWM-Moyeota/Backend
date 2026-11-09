package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.matching.api.dto.PartyClosedEvent;
import team.codingforest.moyeota.matching.api.dto.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.api.dto.PartyMemberLeftEvent;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.AutoCloseResult;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingPlan;
import team.codingforest.moyeota.matching.party.domain.NearbyReservation;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyChangeNotifier;
import team.codingforest.moyeota.matching.party.domain.PartyConflictRule;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.dto.MatchCandidate;
import team.codingforest.moyeota.matching.party.dto.MemberArrived;
import team.codingforest.moyeota.matching.party.dto.MemberJoined;
import team.codingforest.moyeota.matching.party.dto.MemberLeft;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;
import team.codingforest.moyeota.matching.party.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.party.dto.OpenReservationCommand;
import team.codingforest.moyeota.matching.party.dto.PartyClosed;
import team.codingforest.moyeota.matching.party.dto.PartyConfirmed;
import team.codingforest.moyeota.matching.party.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.party.dto.PartyResult;
import team.codingforest.moyeota.matching.party.dto.PartyStatusResponse;
import team.codingforest.moyeota.matching.party.dto.RealTimeMatchingCommand;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.record.domain.RideRecords;
import team.codingforest.moyeota.matching.route.RouteService;
import team.codingforest.moyeota.matching.route.domain.Coordinate;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;
import team.codingforest.moyeota.matching.route.domain.WalkRouteFinder;
import team.codingforest.moyeota.matching.route.domain.WalkingEstimate;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 *  방 유스케이스 - 규칙은 Party 가 정하고 여기서는 잠금·저장·이벤트·SSE 를 묶는다.
 *  방을 바꾸는 메서드는 모두 행을 잠그고(findByIdForUpdate) 커밋 뒤에만 SSE 를 보낸다
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PartyService {
    static final int MAP_LIST_LIMIT = 100;
    private static final int RADIUS = 300;
    private static final int RESERVATION_SEARCH_RADIUS = 1000;
    private static final int RESERVATION_SEARCH_DAYS = 7;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration SOON_WINDOW = Duration.ofMinutes(30);

    private final Parties parties;
    private final ApplicationEventPublisher eventPublisher;
    private final RouteService routeService;
    private final UserAccess userAccess;
    private final AfterCommitExecutor afterCommitExecutor;
    private final PartyChangeNotifier partyChangeNotifier;
    private final WalkRouteFinder walkRouteFinder;
    private final RideRecords rideRecords;
    private final PartyOpener partyOpener;


    public PartyResult create(OpenPartyCommand command) {
        PartyConflictRule.check(parties, command.creatorId(), Instant.now());

        RouteEstimate estimate = routeService.estimate(command.departureLat(), command.departureLng(), command.destinationLat(), command.destinationLng());

        return partyOpener.open(command, estimate);
    }

    public PartyResult reserve(OpenReservationCommand command) {
        return partyOpener.reserve(command);
    }

    @Transactional
    public PartyStatusResponse join(Long partyId, Long memberId) {
        Instant now = Instant.now();

        parties.lockMember(memberId);
        PartyConflictRule.checkNotInRealtime(parties, memberId);

        MemberSummary summary = userAccess.findMemberSummaries(List.of(memberId)).get(memberId);
        int rideCount = parties.countFinishedRides(List.of(memberId)).getOrDefault(memberId, 0);

        Party party = lock(partyId);

        if(!party.isMember(memberId)) {
            PartyConflictRule.checkTimeConflict(parties, memberId, party.isReservation() ? party.getDepartureAt() : now);
        }

        party.join(memberId, now);
        Party saved = parties.save(party);

        eventPublisher.publishEvent(new PartyMemberJoinedEvent(partyId, memberId));
        MemberJoined event = MemberJoined.from(PartyDetailResult.MemberInfo.of(summary, rideCount, saved.isHost(memberId), false), saved.getStatus().name(), saved.getVersion());
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.memberJoined(partyId, event));

        log.info("매칭방 참여 partyId={}, memberId={}, members={}", partyId, memberId, saved.getMembers().size());

        return PartyStatusResponse.from(saved);
    }

    @Transactional
    public void leave(Long partyId, Long memberId) {
        MemberSummary summary = userAccess.findMemberSummaries(List.of(memberId)).get(memberId);
        UUID publicId = summary == null ? null : summary.publicId();

        Party party = lock(partyId);
        party.leave(memberId);
        parties.save(party);

        eventPublisher.publishEvent(new PartyMemberLeftEvent(partyId, memberId));

        if(party.getStatus() == PartyStatus.CANCELED) {
            publishClosed(party);
        } else {
            Long hostId = party.getHostId();   // leave 반영 후 = 승계 결과. 이 분기에선 멤버가 반드시 남아 있다
            MemberSummary hostSummary = userAccess.findMemberSummaries(List.of(hostId)).get(hostId);
            UUID hostPublicId = hostSummary == null ? null : hostSummary.publicId();

            MemberLeft event = MemberLeft.from(publicId, party.getStatus().name(), hostPublicId, party.getVersion());
            afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.memberLeft(partyId, memberId, event));
        }

        log.info("매칭방 나감 partyId={}, memberId={}, status={}, members={}", partyId, memberId, party.getStatus(), party.getMembers().size());
    }

    // ───────────────────────── 약속·도착·출발 ─────────────────────────

    @Transactional
    public void confirm(Long partyId, Long memberId, MeetingPlan plan) {
        Party party = lock(partyId);
        party.confirm(memberId, plan, Instant.now());
        parties.save(party);

        PartyConfirmed event = PartyConfirmed.from(party);
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.confirmed(partyId, event));

        log.info("약속 확정 partyId={}, memberId={}, departureAt={}, departure={}, members={}", partyId, memberId, party.getDepartureAt(), party.getDeparture(), party.getMembers().size());
    }

    @Transactional
    public void arrive(Long partyId, Long memberId, double latitude, double longitude) {
        Location current = new Location(latitude, longitude);

        MemberSummary summary = userAccess.findMemberSummaries(List.of(memberId)).get(memberId);
        UUID publicId = summary == null ? null : summary.publicId();

        Party party = lock(partyId);
        boolean nearDeparture = parties.isNearDeparture(partyId, current.latitude(), current.longitude(), Party.ARRIVAL_RADIUS_METERS);

        if(!party.arrive(memberId, nearDeparture, Instant.now())) {
            log.info("이미 도착한 멤버가 다시 도착을 누름 partyId={}, memberId={}", partyId, memberId);
            return;
        }

        parties.save(party);

        MemberArrived event = MemberArrived.from(publicId, party.getVersion());
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.memberArrived(partyId, event));

        log.info("출발지 도착 partyId={}, memberId={}, version={}", partyId, memberId, party.getVersion());
    }

    /** 도착한 방장이 출발을 누르면 방이 끝나고 탑승 기록이 남는다 */
    @Transactional
    public void depart(Long partyId, Long memberId) {
        Party party = lock(partyId);
        party.depart(memberId, Instant.now());
        parties.save(party);

        rideRecords.save(RideRecord.droppedOffNow(partyId, party.getDeparture(), party.getDestination()));
        publishClosed(party);

        log.info("출발로 동승 종료 partyId={}, memberId={}, riders={}", partyId, memberId, party.riderIds());
    }

    // ───────────────────────── 스케줄러가 닫는 길 ─────────────────────────

    /** 모집 만료 스케줄러 - 혼자인 방만 닫힌다. 2명 이상이면 Party 가 건너뛴다 */
    @Transactional
    public void expireRecruiting(Long partyId) {
        Party party = lock(partyId);

        if(!party.expireRecruiting()) {
            log.info("모집 만료 건너 뜀(혼자가 아니거나 닫힌 방) partyId={}, status={}, members={}", partyId, party.getStatus(), party.getMembers().size());
            return;
        }

        parties.save(party);
        publishClosed(party);

        log.info("모집 만료로 취소 partyId={}", partyId);
    }

    /** 정리 스케줄러 - 아무도 출발을 누르지 않은 방을 닫는다. 도착한 멤버가 있어 FINISHED 로 끝난 방만 탑승 기록을 남긴다 */
    @Transactional
    public void autoClose(Long partyId) {
        Party party = lock(partyId);
        AutoCloseResult result = party.autoClose();

        if(!result.handled()) {
            log.info("자동 정리 건너 뜀(이미 닫힌 방) partyId={}, status={}", partyId, result.status());
            return;
        }

        parties.save(party);

        if(result.hasRiders()) {
            rideRecords.save(RideRecord.droppedOffNow(partyId, party.getDeparture(), party.getDestination()));
        }

        publishClosed(party);

        log.info("방 자동 정리 partyId={}, status={}, members={}, riders={}", partyId, result.status(), party.getMembers().size(), result.riderIds());
    }

    @Transactional(readOnly = true)
    public PartyDetailResult getPartyDetail(Long partyId) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        List<Long> memberIds = party.memberIds();

        return PartyDetailResult.from(party, userAccess.findMemberSummaries(memberIds), parties.countFinishedRides(memberIds));
    }

    @Transactional(readOnly = true)
    public List<PartyResult> findActiveParties() {
        return parties.findRealtimeByStatus(PartyStatus.ACTIVE).stream()
                .map(PartyResult::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PartySummary> findActivePartiesWithin(double swLat, double swLng, double neLat, double neLng) {
        if(swLat >= neLat || swLng >= neLng) {
			throw new BusinessException(MatchingErrorCode.INVALID_MAP_BOUNDS);
		}

        return parties.findSummariesWithinBounds(PartyStatus.ACTIVE, swLat, neLat, swLng, neLng, MAP_LIST_LIMIT);
    }

    /** 출발 시각이 기간 안인 예약 방. 이미 출발한 방은 참여할 수 없어 뺀다 */
    @Transactional(readOnly = true)
    public List<ReservationSummary> findReservations(Instant from, Instant to) {
        if(!from.isBefore(to)) {
			throw new BusinessException(MatchingErrorCode.INVALID_TIME_RANGE);
		}

        Instant now = Instant.now();

        return parties.findReservations(from.isAfter(now) ? from : now, to, MAP_LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ReservationSummary> findSoonReservationsWithin(double swLat, double swLng, double neLat, double neLng) {
        if(swLat >= neLat || swLng >= neLng) {
			throw new BusinessException(MatchingErrorCode.INVALID_MAP_BOUNDS);
		}

        Instant now = Instant.now();

        return parties.findReservationsWithinBounds(now, now.plus(SOON_WINDOW), swLat, neLat, swLng, neLng, MAP_LIST_LIMIT);
    }

    /**
     *  출발지 반경 1km 안에서 date(KST) 하루 동안 출발하는 ACTIVE 예약 방. 오늘이면 이미 출발한 방은 뺀다.
     *  도착지를 넣으면 도착지도 1km 안인 방이 먼저 오고, 빼면 출발 시각 순이다
     */
    @Transactional(readOnly = true)
    public List<NearbyReservation> searchReservations(double depLat, double depLng, Double destLat, Double destLng, LocalDate date) {
        if((destLat == null) != (destLng == null)) {
			throw new BusinessException(MatchingErrorCode.INCOMPLETE_DESTINATION);
		}

        Location departure = new Location(depLat, depLng);
        Location destination = destLat == null ? null : new Location(destLat, destLng);

        Instant now = Instant.now();
        LocalDate today = LocalDate.ofInstant(now, KST);

        if(date.isBefore(today) || date.isAfter(today.plusDays(RESERVATION_SEARCH_DAYS))) {
			throw new BusinessException(MatchingErrorCode.INVALID_SEARCH_DATE);
		}

        Instant dayStart = date.atStartOfDay(KST).toInstant();
        Instant from = now.isAfter(dayStart) ? now : dayStart;
        Instant to = date.plusDays(1).atStartOfDay(KST).toInstant();

        return parties.findReservationsWithinRadius(from, to, departure, destination, RESERVATION_SEARCH_RADIUS, MAP_LIST_LIMIT);
    }

    public List<MatchCandidate> realtimeMatch(Long memberId, RealTimeMatchingCommand command) {
        Location departureLocation = new Location(command.departureLat(), command.departureLng());
        Location destinationLocation = new Location(command.destLat(), command.destLng());

        PartyConflictRule.check(parties, memberId, Instant.now());

        List<NearbyPartySummary> summary = parties.findMatchesWithinRadius(departureLocation.latitude(), departureLocation.longitude(), destinationLocation.latitude(), destinationLocation.longitude(), RADIUS);

        Coordinate departure = Coordinate.from(departureLocation.latitude(), departureLocation.longitude());
        Coordinate destination = Coordinate.from(destinationLocation.latitude(), destinationLocation.longitude());

        List<WalkingEstimate> toPickup = walkRouteFinder.fromTo(departure, summary.stream()
                .map(p -> new Coordinate(p.departureLat(), p.departureLng())).toList());
        List<WalkingEstimate> dropOff = walkRouteFinder.toFrom(destination, summary.stream()
                .map(p -> new Coordinate(p.destinationLat(), p.destinationLng())).toList());

        return IntStream.range(0, summary.size())
                .filter(i -> toPickup.get(i) != null && dropOff.get(i) != null)
                .mapToObj(i -> new MatchCandidate(summary.get(i), toPickup.get(i).durationSeconds() + dropOff.get(i).durationSeconds()))
                .sorted(Comparator.comparingInt(MatchCandidate::totalSeconds))
                .toList();
    }

    private Party lock(Long partyId) {
        return parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));
    }

    /** 닫힌 방 - 모듈 이벤트(채팅방 정리 등)와 SSE closed 를 함께 보낸다. SSE 는 커밋 뒤에만 나간다 */
    private void publishClosed(Party party) {
        Long partyId = party.getId();
        eventPublisher.publishEvent(new PartyClosedEvent(partyId, party.getStatus().name()));

        PartyClosed closed = PartyClosed.from(party.getStatus().name());
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.closed(partyId, closed));
    }
}
