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
import team.codingforest.moyeota.matching.party.completion.PartyCompletionPolicy;
import team.codingforest.moyeota.matching.party.domain.PartyChangeNotifier;
import team.codingforest.moyeota.matching.party.dto.MemberArrived;
import team.codingforest.moyeota.matching.party.dto.MemberJoined;
import team.codingforest.moyeota.matching.party.dto.MemberLeft;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;
import team.codingforest.moyeota.matching.party.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.party.dto.OpenReservationCommand;
import team.codingforest.moyeota.matching.party.dto.RecruitingClosed;
import team.codingforest.moyeota.matching.party.dto.PartyClosed;
import team.codingforest.moyeota.matching.party.dto.PartyConfirmed;
import team.codingforest.moyeota.matching.party.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.party.dto.PartyResult;
import team.codingforest.moyeota.matching.party.dto.PartyStatusResponse;
import team.codingforest.moyeota.matching.party.domain.AutoCloseResult;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.NearbyReservation;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyConflictRule;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
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
import team.codingforest.moyeota.user.api.NoShowPenalty;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class PartyService {
    static final int MAP_LIST_LIMIT = 100;
    private static final int RADIUS = 300;
    /**
     * 예약 방 검색 반경(m). 출발지는 이 안이어야 하고, 도착지는 이 안이면 근처로 앞에 세운다
     */
    private static final int RESERVATION_SEARCH_RADIUS = 1000;
    /**
     * 예약 방 검색 날짜는 오늘(KST)부터 이 일수 뒤까지 - 예약 방은 7일 뒤까지 만들 수 있다
     */
    private static final int RESERVATION_SEARCH_DAYS = 7;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration SOON_WINDOW = Duration.ofMinutes(30);

    private final Parties parties;
    private final ApplicationEventPublisher eventPublisher;
    private final RouteService routeService;
    private final UserAccess userAccess;
    private final PartyCompletionPolicy partyCompletionPolicy;
    private final AfterCommitExecutor afterCommitExecutor;
    private final PartyChangeNotifier partyChangeNotifier;
    private final WalkRouteFinder walkRouteFinder;
    private final RideRecords rideRecords;
	private final PartyOpener partyOpener;
    private final NoShowPenalty noShowPenalty;
    private final MeetingNotificationService meetingNotificationService;

    public PartyResult open(OpenPartyCommand command) {
        Instant now = Instant.now();

        ensureNotRestricted(command.creatorId(), now);
        PartyConflictRule.check(parties, command.creatorId(), now);

        RouteEstimate estimate = routeService.estimate(command.departureLat(), command.departureLng(), command.destinationLat(), command.destinationLng());

		return partyOpener.open(command, estimate);
    }

    public PartyResult reserve(OpenReservationCommand command) {
        ensureNotRestricted(command.creatorId(), Instant.now());

        return partyOpener.reserve(command);
    }

    @Transactional
    public PartyStatusResponse join(Long partyId, Long memberId) {
        ensureNotRestricted(memberId, Instant.now());
        parties.lockMember(memberId);
        PartyConflictRule.checkNotInRealtime(parties, memberId);

        MemberSummary summary = userAccess.findMemberSummaries(List.of(memberId)).get(memberId);
        int rideCount = parties.countFinishedRides(List.of(memberId)).getOrDefault(memberId, 0);

        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        if (!party.hasMember(memberId)) {
            PartyConflictRule.checkTimeConflict(parties, memberId, party.isReservation() ? party.getDepartureAt() : Instant.now());
        }

        party.join(memberId);

        if (party.isFull()) {
            partyCompletionPolicy.onCompleted(party);
        }

        log.info("매칭방에 사용자 참가됨 partyId={}, memberId={}, status={}", partyId, memberId, party.getStatus());

        Party saved = parties.save(party);

        eventPublisher.publishEvent(new PartyMemberJoinedEvent(partyId, memberId));

        MemberJoined event = MemberJoined.from(PartyDetailResult.MemberInfo.of(summary, rideCount, saved.isHost(memberId), false), saved.getStatus().name(), saved.getVersion());

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
        return parties.findRealtimeByStatus(PartyStatus.ACTIVE).stream()
                .map(PartyResult::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PartySummary> findActivePartiesWithin(double swLat, double swLng, double neLat, double neLng) {
        if (swLat >= neLat || swLng >= neLng) throw new BusinessException(MatchingErrorCode.INVALID_MAP_BOUNDS);

        return parties.findSummariesWithinBounds(PartyStatus.ACTIVE, swLat, neLat, swLng, neLng, MAP_LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ReservationSummary> findReservations(Instant from, Instant to) {
        if (!from.isBefore(to)) throw new BusinessException(MatchingErrorCode.INVALID_TIME_RANGE);

        return parties.findReservations(from, to, MAP_LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ReservationSummary> findSoonReservationsWithin(double swLat, double swLng, double neLat, double neLng) {
        if (swLat >= neLat || swLng >= neLng) throw new BusinessException(MatchingErrorCode.INVALID_MAP_BOUNDS);

        Instant now = Instant.now();

        return parties.findReservationsWithinBounds(now, now.plus(SOON_WINDOW), swLat, neLat, swLng, neLng, MAP_LIST_LIMIT);
    }

    /**
     *  출발지 반경 1km 안에서 date(KST) 하루 동안 출발하는 ACTIVE 예약 방. 오늘이면 이미 출발한 방은 뺀다.
     *  도착지를 넣으면 도착지도 1km 안인 방이 먼저 오고, 빼면 출발 시각 순이다
     */
    @Transactional(readOnly = true)
    public List<NearbyReservation> searchReservations(double depLat, double depLng, Double destLat, Double destLng, LocalDate date) {
        if ((destLat == null) != (destLng == null)) throw new BusinessException(MatchingErrorCode.INCOMPLETE_DESTINATION);

        Location departure = new Location(depLat, depLng);
        Location destination = destLat == null ? null : new Location(destLat, destLng);

        Instant now = Instant.now();
        LocalDate today = LocalDate.ofInstant(now, KST);

        if (date.isBefore(today) || date.isAfter(today.plusDays(RESERVATION_SEARCH_DAYS))) throw new BusinessException(MatchingErrorCode.INVALID_SEARCH_DATE);

        Instant dayStart = date.atStartOfDay(KST).toInstant();
        Instant from = now.isAfter(dayStart) ? now : dayStart;
        Instant to = date.plusDays(1).atStartOfDay(KST).toInstant();

        return parties.findReservationsWithinRadius(from, to, departure, destination, RESERVATION_SEARCH_RADIUS, MAP_LIST_LIMIT);
    }

    @Transactional
    public void confirm(Long partyId, Long memberId, Instant departureAt) {
        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        // 정원이 차 이미 COMPLETED 인 방은 참여 때 완료 정책을 돌렸다 - 이번 확정으로 모집이 끝나는 ACTIVE 방만 돌린다
        boolean closesRecruiting = party.getStatus() == PartyStatus.ACTIVE;

        party.confirm(memberId, departureAt, Instant.now());

        if (closesRecruiting) {
            partyCompletionPolicy.onCompleted(party);
        }

        parties.save(party);

        PartyConfirmed event = PartyConfirmed.from(party.getStatus().name(), party.getDepartureAt(), party.getVersion());
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.confirmed(partyId, event));

        log.info("방장이 약속 시간 확정 partyId={}, memberId={}, departureAt={}, status={}, members={}", partyId, memberId, party.getDepartureAt(), party.getStatus(), party.getMembers().size());
    }

    @Transactional
    public void arrive(Long partyId, Long memberId, double latitude, double longitude) {
        // 요청온 위도 경도 유효한지 확인
        Location current = new Location(latitude, longitude);

        MemberSummary summary = userAccess.findMemberSummaries(List.of(memberId)).get(memberId);
        UUID publicId = summary == null ? null : summary.publicId();

        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

		// 출발지 반경 100m 있는지 확인
        boolean nearDeparture = parties.isNearDeparture(partyId, current.latitude(), current.longitude(), Party.ARRIVAL_RADIUS_METERS);

		// 중복 요청 처리
        if (!party.arrive(memberId, nearDeparture, Instant.now())) {
            log.info("이미 도착한 멤버가 다시 도착을 누름 partyId={}, memberId={}", partyId, memberId);
            return;
        }

        parties.save(party);

        MemberArrived event = MemberArrived.from(publicId, party.getVersion());
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.memberArrived(partyId, event));

        log.info("출발지 도착 partyId={}, memberId={}, version={}", partyId, memberId, party.getVersion());
    }

    /**
     *  도착한 멤버가 출발을 누르면 방이 끝난다. 그 순간 도착하지 않은 멤버는 노쇼로 저장되고 탑승 기록·탑승 횟수에서 빠지며,
     *  같은 트랜잭션에서 노쇼 제재(누적 횟수 +1, 약속 시간부터 누적 횟수 × 24시간 이용 제한)가 더해지고, 커밋 후 노쇼 멤버에게 노쇼 알림이 나간다(방해 금지 시간 제외)
     */
    @Transactional
    public void depart(Long partyId, Long memberId) {
        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        Instant now = Instant.now();
        List<Long> noShowMemberIds = party.depart(memberId, now);
        parties.save(party);
        penalizeNoShows(party, noShowMemberIds, now);

        meetingNotificationService.notifyNoShows(partyId, noShowMemberIds, now);
        rideRecords.save(RideRecord.withoutDriver(partyId, party.getDeparture(), party.getDestination()));
        eventPublisher.publishEvent(new PartyClosedEvent(partyId, party.getStatus().name()));
        PartyClosed closed = PartyClosed.from(party.getStatus().name());
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.closed(partyId, closed));

        log.info("출발로 합승 종료 partyId={}, memberId={}, members={}, noShowMemberIds={}", partyId, memberId, party.getMembers().size(), noShowMemberIds);
    }

    @Transactional
    public void expireRecruiting(Long partyId) {
        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        party.expireRecruiting();

        if (party.getStatus() == PartyStatus.CANCELED) {
            parties.save(party);
            eventPublisher.publishEvent(new PartyClosedEvent(partyId, party.getStatus().name()));
            PartyClosed closed = PartyClosed.from(party.getStatus().name());
            afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.closed(partyId, closed));
        } else {
            partyCompletionPolicy.onCompleted(party);
            parties.save(party);
            notifyRecruitingClosed(party);
        }

        log.info("모집 시간 만료 partyId={}, status={}, members={}", partyId, party.getStatus(), party.getMembers().size());
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

        log.info("합승 종료 partyId={}, memberId={}", partyId, memberId);
    }

    /**
     *  정리 스케줄러가 생성(실시간)·출발(예약) 후 1일이 지나도 열려 있는 방을 닫는다. 노쇼 판정은 Party.autoClose 가 한다.
     *  도착한 멤버가 있어 FINISHED 로 끝난 방만 하차 기록을 남긴다. 노쇼로 찍힌 멤버는 같은 트랜잭션에서 제재가 더해지고 커밋 후 노쇼 알림을 받는다(방해 금지 시간 제외)
     *
     *  @return 이번에 노쇼로 찍힌 memberId (합류 순)
     */
    @Transactional
    public List<Long> autoClose(Long partyId) {
        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        Instant now = Instant.now();
        AutoCloseResult result = party.autoClose(now);
        parties.save(party);
        penalizeNoShows(party, result.noShowMemberIds(), now);
        meetingNotificationService.notifyNoShows(partyId, result.noShowMemberIds(), now);

        if (result.hasRiders()) {
            rideRecords.save(RideRecord.withoutDriver(partyId, party.getDeparture(), party.getDestination()));
        }

        eventPublisher.publishEvent(new PartyClosedEvent(partyId, party.getStatus().name()));
        PartyClosed closed = PartyClosed.from(party.getStatus().name());
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.closed(partyId, closed));

        log.info("방 자동 정리 partyId={}, status={}, members={}, arrived={}, noShows={}, noShowMemberIds={}", partyId, result.status(),
                party.getMembers().size(), result.arrivedCount(), result.noShowMemberIds().size(), result.noShowMemberIds());

        return result.noShowMemberIds();
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

    /**
     *  노쇼 제재 중이면 방 만들기(실시간·예약)·참여를 막는다. 채팅·탑승 기록 등은 막지 않는다
     */
    private void ensureNotRestricted(Long memberId, Instant now) {
        noShowPenalty.findRestrictedUntil(memberId, now).ifPresent(until -> {
            log.info("노쇼 제재 중이라 거절 memberId={}, restrictedUntil={}", memberId, until);
            throw new BusinessException(MatchingErrorCode.NO_SHOW_RESTRICTED);
        });
    }

    /**
     *  노쇼로 찍힌 멤버마다 제재를 더한다. 제한은 기록한 시각이 아니라 방의 약속 시간(departureAt)부터 센다 - 노쇼는 약속이 정해진 방에서만 나와 비어 있지 않다.
     *  users 행을 잠그므로 다른 트랜잭션과 잠금 순서가 엇갈려 교착되지 않게 userId 오름차순으로 부른다
     */
    private void penalizeNoShows(Party party, List<Long> noShowMemberIds, Instant now) {
        noShowMemberIds.stream()
                .sorted()
                .forEach(userId -> noShowPenalty.record(userId, party.getDepartureAt(), now));
    }

    private Party getParty(Long partyId) {
        return parties.findById(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));
    }

    private void notifyRecruitingClosed(Party party) {
        Long partyId = party.getId();
        RecruitingClosed event = RecruitingClosed.from(party.getStatus().name(), party.getVersion());
        afterCommitExecutor.execute("party.sse", () -> partyChangeNotifier.recruitingClosed(partyId, event));
    }
}
