package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.record.FakeRideRecords;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.matching.api.dto.PartyClosedEvent;
import team.codingforest.moyeota.matching.api.dto.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.api.dto.PartyMemberLeftEvent;
import team.codingforest.moyeota.matching.party.dto.MatchCandidate;
import team.codingforest.moyeota.matching.party.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.party.dto.OpenReservationCommand;
import team.codingforest.moyeota.matching.party.dto.RealTimeMatchingCommand;
import team.codingforest.moyeota.matching.party.dto.MemberArrived;
import team.codingforest.moyeota.matching.party.dto.MemberJoined;
import team.codingforest.moyeota.matching.party.dto.MemberLeft;
import team.codingforest.moyeota.matching.party.dto.PartyClosed;
import team.codingforest.moyeota.matching.party.dto.PartyConfirmed;
import team.codingforest.moyeota.matching.party.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.party.dto.PartyResult;
import team.codingforest.moyeota.matching.party.dto.PartyStatusResponse;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingPlan;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyChangeNotifier;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.domain.RoomType;
import team.codingforest.moyeota.matching.route.RouteService;
import team.codingforest.moyeota.matching.route.domain.Coordinate;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;
import team.codingforest.moyeota.matching.route.domain.WalkRouteFinder;
import team.codingforest.moyeota.matching.route.domain.WalkingEstimate;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class PartyServiceTest {
    private static final Long host = 1L;
    private static final Long participant = 2L;
    private static final Long anotherHost = 3L;
    private static final Long guest = 4L;

    private PartyJpaTest parties;
    private RecordingEventPublisher events;
    private FakeUserAccess userAccess;
    private RecordingPartyChangeNotifier signals;
    private FakeRideRecords rideRecords;
    private PartyService service;

    @BeforeEach
    void setUp() {
        parties = new PartyJpaTest();
        events = new RecordingEventPublisher();
        userAccess = new FakeUserAccess();
        signals = new RecordingPartyChangeNotifier();
        rideRecords = new FakeRideRecords(parties);
        userAccess.등록(host, "방장");
        userAccess.등록(participant, "동승자");
        service = serviceWith(IMMEDIATE);
    }

    /** 커밋 후 실행기를 갈아끼운 서비스 - 단위 테스트에는 트랜잭션이 없으므로 기본은 즉시 실행(IMMEDIATE) */
    private PartyService serviceWith(AfterCommitExecutor afterCommit) {
        RouteService routeService = new RouteService(
                key -> new RouteEstimate(12000, 25, "_p~iF~ps|U_ulLnnqC"),   // RouteFinder 가짜 (네이버 미호출)
                new RouteCacheTest());
        return new PartyService(parties, events, routeService, userAccess, afterCommit, signals,
                new FakeWalkRouteFinder(), rideRecords, new PartyOpener(parties, events));
    }

    // ───────────────────────── 예약 방 ─────────────────────────

    private OpenReservationCommand reservation(Long creatorId, Instant departureAt) {
        return new OpenReservationCommand(creatorId, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 3, departureAt);
    }

    private Instant 뒤(Duration duration) {
        return Instant.now().plus(duration);
    }

    private void assertFails(Runnable action, MatchingErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }

    @Test
    void 예약_방을_만들면_출발_시각이_저장되고_경로는_비어_있다() {
        Instant 출발 = 뒤(Duration.ofDays(1));

        PartyResult result = service.reserve(reservation(host, 출발));

        assertThat(result.roomType()).isEqualTo("RESERVED");
        assertThat(result.departureAt()).isEqualTo(출발);
        assertThat(result.route()).isNull();
        assertThat(result.estimateFare()).isNull();
        assertThat(result.currentMembers()).isEqualTo(1);
    }

    @Test
    void 예약_방을_만들면_채팅방이_열리도록_참여_이벤트를_낸다() {
        PartyResult result = service.reserve(reservation(host, 뒤(Duration.ofDays(1))));

        assertThat(events.published).contains(new PartyMemberJoinedEvent(result.id(), host));
    }

    @Test
    void 실시간_방에_있으면_예약_방을_만들_수_없다() {
        service.open(createParty(host, 3));

        assertFails(() -> service.reserve(reservation(host, 뒤(Duration.ofDays(1)))), MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
    }

    @Test
    void 실시간_방에_있으면_다른_예약_방에_참여할_수_없다() {
        service.open(createParty(participant, 3));
        Long 예약방 = service.reserve(reservation(host, 뒤(Duration.ofDays(1)))).id();

        assertFails(() -> service.join(예약방, participant), MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
    }

    @Test
    void 예약_방이_있어도_세_시간_떨어진_예약_방은_만들_수_있다() {
        service.reserve(reservation(host, 뒤(Duration.ofHours(24))));

        PartyResult result = service.reserve(reservation(host, 뒤(Duration.ofHours(27))));

        assertThat(result.id()).isNotNull();
    }

    @Test
    void 두_시간_안에_겹치는_예약_방은_만들_수_없다() {
        service.reserve(reservation(host, 뒤(Duration.ofHours(24))));

        assertFails(() -> service.reserve(reservation(host, 뒤(Duration.ofHours(25)))), MatchingErrorCode.PARTY_TIME_CONFLICT);
    }

    @Test
    void 정확히_두_시간_차이도_겹치는_것으로_본다() {
        Instant 출발 = 뒤(Duration.ofHours(24));
        service.reserve(reservation(host, 출발));

        assertFails(() -> service.reserve(reservation(host, 출발.plus(Duration.ofHours(2)))), MatchingErrorCode.PARTY_TIME_CONFLICT);
    }

    @Test
    void 곧_출발할_예약_방이_있으면_실시간_방을_만들_수_없다() {
        service.reserve(reservation(host, 뒤(Duration.ofHours(1))));

        assertFails(() -> service.open(createParty(host, 3)), MatchingErrorCode.PARTY_TIME_CONFLICT);
    }

    @Test
    void 예약_방의_출발이_세_시간_뒤면_실시간_방을_만들_수_있다() {
        service.reserve(reservation(host, 뒤(Duration.ofHours(3))));

        PartyResult result = service.open(createParty(host, 3));

        assertThat(result.roomType()).isEqualTo("REALTIME");
    }

    @Test
    void 곧_출발할_예약_방이_있으면_실시간_방에_참여할_수_없다() {
        Long 실시간방 = service.open(createParty(anotherHost, 3)).id();
        service.reserve(reservation(host, 뒤(Duration.ofHours(1))));

        assertFails(() -> service.join(실시간방, host), MatchingErrorCode.PARTY_TIME_CONFLICT);
    }

    @Test
    void 취소된_예약_방은_시간이_겹쳐도_문제가_되지_않는다() {
        // 혼자인 예약 방은 출발 시각에 모집 만료로 취소된다
        Instant 출발 = 뒤(Duration.ofHours(24));
        Long 예약방 = service.reserve(reservation(host, 출발)).id();
        service.expireRecruiting(예약방);

        PartyResult result = service.reserve(reservation(host, 출발));

        assertThat(result.id()).isNotNull();
    }

    @Test
    void 이미_들어간_예약_방에_다시_참여하면_시간_충돌이_아니라_중복_참여로_거절한다() {
        Long 예약방 = service.reserve(reservation(host, 뒤(Duration.ofDays(1)))).id();

        assertFails(() -> service.join(예약방, host), MatchingErrorCode.ALREADY_JOINED_PARTY);
    }

    @Test
    void 예약_방이_있어도_시간이_겹치지_않으면_실시간_후보를_조회할_수_있다() {
        service.reserve(reservation(host, 뒤(Duration.ofDays(1))));

        List<MatchCandidate> candidates = service.realtimeMatch(host, new RealTimeMatchingCommand(37.4979, 127.0276, 37.3948, 127.1112));

        assertThat(candidates).isEmpty();
    }

    @Test
    void 실시간_목록에는_예약_방이_나오지_않는다() {
        Long 실시간방 = service.open(createParty(anotherHost, 3)).id();
        service.reserve(reservation(host, 뒤(Duration.ofDays(1))));

        assertThat(service.findActiveParties()).extracting(PartyResult::id).containsExactly(실시간방);
        assertThat(service.findActivePartiesWithin(37.0, 126.0, 38.0, 128.0)).extracting(PartySummary::id).containsExactly(실시간방);
    }

    @Test
    void 예약_목록은_기간_안의_방을_출발_시각_순으로_돌려준다() {
        Long 늦은방 = service.reserve(reservation(host, 뒤(Duration.ofHours(30)))).id();
        Long 이른방 = service.reserve(reservation(participant, 뒤(Duration.ofHours(20)))).id();
        service.reserve(reservation(anotherHost, 뒤(Duration.ofDays(5))));

        List<ReservationSummary> found = service.findReservations(뒤(Duration.ofHours(1)), 뒤(Duration.ofDays(2)));

        assertThat(found).extracting(ReservationSummary::id).containsExactly(이른방, 늦은방);
    }

    @Test
    void 예약_목록의_시작이_끝보다_늦으면_거절한다() {
        assertFails(() -> service.findReservations(뒤(Duration.ofDays(2)), 뒤(Duration.ofDays(1))), MatchingErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    void 곧_출발_목록은_30분_안에_출발하는_예약_방만_돌려준다() {
        Long 곧출발 = service.reserve(reservation(host, 뒤(Duration.ofMinutes(20)))).id();
        service.reserve(reservation(participant, 뒤(Duration.ofMinutes(40))));

        List<ReservationSummary> found = service.findSoonReservationsWithin(37.0, 126.0, 38.0, 128.0);

        assertThat(found).extracting(ReservationSummary::id).containsExactly(곧출발);
    }

    @Test
    void 곧_출발_목록은_영역_밖의_방을_돌려주지_않는다() {
        service.reserve(reservation(host, 뒤(Duration.ofMinutes(20))));

        assertThat(service.findSoonReservationsWithin(36.0, 126.0, 36.5, 128.0)).isEmpty();
    }

    @Test
    void 곧_출발_목록의_영역이_뒤집히면_거절한다() {
        assertFails(() -> service.findSoonReservationsWithin(38.0, 126.0, 37.0, 128.0), MatchingErrorCode.INVALID_MAP_BOUNDS);
    }

    // ───────────────────────── 약속 시간 확정 ─────────────────────────

    @Test
    void 방장이_약속_시간을_확정하면_COMPLETED_가_되고_구독자에게_약속_시간과_출발지를_알린다() {
        Long 방 = service.open(createParty(host, 3)).id();
        service.join(방, participant);
        Instant 약속 = 뒤(Duration.ofMinutes(30));

        service.confirm(방, host, MeetingPlan.at(약속));

        Party saved = parties.findById(방).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(saved.getDepartureAt()).isEqualTo(약속);
        assertThat(saved.isConfirmed()).isTrue();
        assertThat(signals.sent).containsExactly(방 + ":member-joined", 방 + ":confirmed");
        assertThat(signals.confirmed).containsExactly(new PartyConfirmed("COMPLETED", 약속, 37.4979, 127.0276, "강남역", 2L));
    }

    @Test
    void 만남_장소를_주고_확정하면_출발지가_바뀌어_상세와_confirmed_에_실린다() {
        Long 방 = service.open(createParty(host, 3)).id();
        service.join(방, participant);
        Instant 약속 = 뒤(Duration.ofMinutes(30));

        service.confirm(방, host, new MeetingPlan(약속, new Location(37.4985, 127.0290), "강남역 2번 출구"));

        PartyDetailResult detail = service.getPartyDetail(방);
        assertThat(detail.departureLat()).isEqualTo(37.4985);
        assertThat(detail.departureLng()).isEqualTo(127.0290);
        assertThat(detail.departure()).isEqualTo("강남역 2번 출구");
        assertThat(signals.confirmed.getLast()).isEqualTo(new PartyConfirmed("COMPLETED", 약속, 37.4985, 127.0290, "강남역 2번 출구", 2L));
    }

    @Test
    void 없는_방의_약속_시간은_확정할_수_없다() {
        assertFails(() -> service.confirm(999L, host, MeetingPlan.at(뒤(Duration.ofMinutes(30)))), MatchingErrorCode.PARTY_NOT_FOUND);
    }

    @Test
    void 확정한_방에서_둘_중_하나가_나가면_취소되고_closed_신호를_보낸다() {
        // 약속이 잡힌 뒤라 더 모을 수 없고 혼자서는 출발할 수 없다 - 남은 사람도 홈으로 돌아가야 한다
        Long 방 = service.open(createParty(host, 3)).id();
        service.join(방, participant);
        service.confirm(방, host, MeetingPlan.at(뒤(Duration.ofMinutes(30))));
        signals.sent.clear();

        service.leave(방, participant);

        assertThat(parties.findById(방).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(parties.findById(방).orElseThrow().getMembers()).extracting(PartyMember::getMemberId).containsExactly(host);
        assertThat(events.leftFor(방, participant)).isEqualTo(1);
        assertThat(events.closedFor(방, "CANCELED")).isEqualTo(1);
        assertThat(signals.sent).as("닫힐 방이라 퇴장 알림 대신 closed 만 보낸다").containsExactly(방 + ":closed");
        assertThat(signals.closed.getLast()).isEqualTo(PartyClosed.from("CANCELED"));
    }

    @Test
    void 확정한_방에서_방장이_나가도_취소된다() {
        Long 방 = service.open(createParty(host, 3)).id();
        service.join(방, participant);
        service.confirm(방, host, MeetingPlan.at(뒤(Duration.ofMinutes(30))));

        service.leave(방, host);

        assertThat(parties.findById(방).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(signals.closed.getLast()).isEqualTo(PartyClosed.from("CANCELED"));
    }

    @Test
    void 확정한_방에서_셋_중_하나가_나가면_그대로_COMPLETED_다() {
        Long 방 = service.open(createParty(host, 3)).id();
        service.join(방, participant);
        service.join(방, guest);
        service.confirm(방, host, MeetingPlan.at(뒤(Duration.ofMinutes(30))));
        signals.sent.clear();

        service.leave(방, guest);

        assertThat(parties.findById(방).orElseThrow().getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(signals.sent).containsExactly(방 + ":member-left:" + guest);
        assertThat(signals.left.getLast().status()).isEqualTo("COMPLETED");
    }

    // ───────────────────────── 모집 만료 ─────────────────────────

    @Test
    void 십오분이_지난_두_명_방은_모집_만료_뒤에도_ACTIVE_다() {
        // 15분은 "혼자면 접는다"는 기준이지 모집 마감이 아니다 - 방장이 확정할 때까지 그대로 두고 알리지도 않는다
        Instant 이십분전 = Instant.now().minus(Duration.ofMinutes(20));
        Party party = Party.create(host, 강남역(), 판교역(), "강남역", "판교역", new Capacity(3), 이십분전, 12000, 25, "_p~iF~ps|U_ulLnnqC");
        party.join(participant, 이십분전.plusSeconds(60));
        Long 방 = parties.save(party).getId();

        service.expireRecruiting(방);

        assertThat(parties.findById(방).orElseThrow().getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(events.closedFor(방, "CANCELED")).isZero();
        assertThat(signals.sent).isEmpty();
    }

    @Test
    void 모집_시간이_끝난_방에_혼자면_취소하고_닫혔다고_알린다() {
        Long 방 = service.open(createParty(host, 3)).id();

        service.expireRecruiting(방);

        assertThat(parties.findById(방).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(events.published).contains(new PartyClosedEvent(방, "CANCELED"));
        assertThat(signals.sent).containsExactly(방 + ":closed");
        assertThat(signals.closed.getLast()).isEqualTo(PartyClosed.from("CANCELED"));
    }

    // ───────────────────────── 생성·참여·상세 ─────────────────────────

    @Test
    void 방을_생성하면_생성자가_멤버로_포함된_ACTIVE_방이_된다() {
        PartyResult result = service.open(createParty(host, 3));

        assertThat(result.id()).isNotNull();
        assertThat(result.currentMembers()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void 정원이_차도_ACTIVE_에_머문다() {
        PartyResult party = service.open(createParty(host, 2));

        service.join(party.id(), participant);

        assertThat(service.getPartyDetail(party.id()).status()).isEqualTo("ACTIVE");
    }

    @Test
    void 혼자_타는_방도_ACTIVE_다() {
        PartyResult party = service.open(createParty(host, 1));

        assertThat(service.getPartyDetail(party.id()).status()).isEqualTo("ACTIVE");
    }

    @Test
    void 정원이_찬_방에는_참여할_수_없다() {
        PartyResult party = service.open(createParty(host, 2));
        service.join(party.id(), participant);

        assertFails(() -> service.join(party.id(), guest), MatchingErrorCode.PARTY_FULL);
    }

    @Test
    void 방에_참여하면_명단에_추가된다() {
        PartyResult result = service.open(createParty(host, 3));

        service.join(result.id(), participant);
        PartyDetailResult detail = service.getPartyDetail(result.id());
        assertThat(detail.currentMembers()).isEqualTo(2);
        // 내부 PK 대신 user 모듈이 준 publicId·닉네임이 실려야 한다
        assertThat(detail.members())
                .extracting(PartyDetailResult.MemberInfo::publicId, PartyDetailResult.MemberInfo::nickname)
                .containsExactlyInAnyOrder(
                        tuple(userAccess.publicIdOf(host), "방장"),
                        tuple(userAccess.publicIdOf(participant), "동승자"));
    }

    @Test
    void 상세_조회에는_가장_먼저_합류한_멤버가_방장으로_실린다() {
        PartyResult party = service.open(createParty(host, 2));
        service.join(party.id(), participant);

        assertThat(service.getPartyDetail(party.id()).members())
                .extracting(PartyDetailResult.MemberInfo::host)
                .containsExactly(true, false);   // 멤버 목록은 합류 순 - 생성자가 방장
    }

    @Test
    void 동승이_끝난_방_수가_탑승_횟수로_나온다() {
        // host 는 한 번 완주, participant 는 처음 - 대기 화면 "탑승 N회" 의 근거
        saveFinishedRide(host, guest);

        PartyResult 새방 = service.open(createParty(host, 3));
        service.join(새방.id(), participant);

        assertThat(service.getPartyDetail(새방.id()).members())
                .extracting(PartyDetailResult.MemberInfo::nickname, PartyDetailResult.MemberInfo::rideCount)
                .containsExactlyInAnyOrder(tuple("방장", 1), tuple("동승자", 0));
    }

    @Test
    void 취소된_방은_탑승_횟수에_들어가지_않는다() {
        PartyResult 취소방 = service.open(createParty(host, 3));
        service.leave(취소방.id(), host);                       // 마지막 멤버가 나가면 CANCELED

        PartyResult 새방 = service.open(createParty(host, 3));

        assertThat(service.getPartyDetail(새방.id()).members())
                .extracting(PartyDetailResult.MemberInfo::rideCount)
                .containsExactly(0);
    }

    @Test
    void 유저_요약이_없는_멤버가_있어도_상세_조회는_깨지지_않는다() {
        // 탈퇴한 유저가 명단에 남아 있는 경우 - 조회 자체가 500 으로 죽으면 대기 화면 전체가 막힌다
        PartyResult result = service.open(createParty(host, 3));
        service.join(result.id(), guest);   // guest 는 userAccess 에 미등록

        PartyDetailResult detail = service.getPartyDetail(result.id());

        assertThat(detail.members()).hasSize(2);
        assertThat(detail.members())
                .filteredOn(m -> m.publicId() == null)
                .singleElement()
                .satisfies(m -> assertThat(m.nickname()).isNull());
    }

    @Test
    void 존재하지_않는_방에_참여하면_예외가_발생한다() {
        assertThatThrownBy(() -> service.join(999L, participant))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_FOUND);
    }

    @Test
    void 나가면_명단에서_빠진다() {
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), participant);

        service.leave(party.id(), participant);

        PartyDetailResult detail = service.getPartyDetail(party.id());
        assertThat(detail.currentMembers()).isEqualTo(1);
    }

    @Test
    void 혼자_남은_사람이_나가면_방이_취소된다() {
        PartyResult party = service.open(createParty(host, 3));

        service.leave(party.id(), host);
        assertThat(service.getPartyDetail(party.id()).status()).isEqualTo("CANCELED");
    }

    // ── 방 닫힘 이벤트 (SSE 대기 화면이 홈으로 돌아가는 신호) ──

    @Test
    void 마지막_멤버가_나가_취소되면_닫힘_이벤트가_발행된다() {
        PartyResult party = service.open(createParty(host, 3));

        service.leave(party.id(), host);

        assertThat(events.closedFor(party.id(), "CANCELED")).isEqualTo(1);
    }

    @Test
    void 사람이_남아_있으면_나가도_닫힘_이벤트는_없다() {
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), participant);

        service.leave(party.id(), participant);

        assertThat(events.leftFor(party.id(), participant)).isEqualTo(1);
        assertThat(events.closedFor(party.id(), "CANCELED")).as("방은 아직 ACTIVE 다").isZero();
    }

    @Test
    void 활성화된_방만_목록에_나온다() {
        PartyResult openParty = service.open(createParty(host, 3));
        PartyResult closeParty = service.open(createParty(anotherHost, 2));

        service.leave(closeParty.id(), anotherHost);   // 마지막 멤버가 나가면 CANCELED

        List<PartyResult> result = service.findActiveParties();

        assertThat(result)
                .extracting(PartyResult::id)
                .containsExactly(openParty.id());
    }

    @Test
    void 참여_중인_방이_있으면_새_방을_만들_수_없다() {
        service.open(createParty(host, 3));

        assertThatThrownBy(() -> service.open(createParty(host, 3)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
    }

    @Test
    void 경로_조회_중_다른_방에_들어갔으면_저장_단계에서_거절한다() {
        // 경로 조회는 트랜잭션 밖이라 첫 검사와 저장 사이에 다른 방에 들어갈 수 있다
        PartyOpener opener = new PartyOpener(parties, events);
        service.open(createParty(host, 3));

        assertThatThrownBy(() -> opener.open(createParty(host, 3), new RouteEstimate(12000, 25, "_p~iF~ps|U_ulLnnqC")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
    }

    @Test
    void 참여_중인_방이_있으면_다른_방에_참여할_수_없다() {
        service.open(createParty(host, 3));
        PartyResult party = service.open(createParty(anotherHost, 3));

        assertThatThrownBy(() -> service.join(party.id(), host))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
    }

    @Test
    void 방이_취소된_뒤에는_새_방을_만들_수_있다() {
        PartyResult party = service.open(createParty(host, 3));

        service.leave(party.id(), host);

        PartyResult newParty = service.open(createParty(host, 3));

        assertThat(newParty.id()).isNotEqualTo(party.id());
    }

    // ───────────────────────── 참여·퇴장 이벤트 (채팅방 초대용) ─────────────────────────

    @Test
    void 방을_만들면_방장의_참여_이벤트가_발행된다() {
        PartyResult party = service.open(createParty(host, 3));

        assertThat(events.joinedFor(party.id(), host)).isEqualTo(1);
    }

    @Test
    void 참여하면_정원_충족_여부와_무관하게_참여_이벤트가_발행된다() {
        PartyResult party = service.open(createParty(host, 2));

        service.join(party.id(), participant);   // 정원 충족 - 상태는 ACTIVE 그대로

        assertThat(events.joinedFor(party.id(), participant))
                .as("정원을 채운 마지막 사람도 채팅방에 들어가야 한다")
                .isEqualTo(1);
    }

    @Test
    void 나가면_퇴장_이벤트가_발행된다() {
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), participant);

        service.leave(party.id(), participant);

        assertThat(events.leftFor(party.id(), participant)).isEqualTo(1);
    }

    // ───────────────────────── SSE 신호 (아웃박스에 남기지 않는 실시간 전파) ─────────────────────────

    @Test
    void 참여하면_들어온_사람_정보와_방_상태를_보낸다() {
        userAccess.등록(participant, "참가자");
        PartyResult party = service.open(createParty(host, 3));

        service.join(party.id(), participant);

        assertThat(signals.sent).containsExactly(party.id() + ":member-joined");
        MemberJoined event = signals.joined.getFirst();
        assertThat(event.member().publicId()).isEqualTo(userAccess.publicIdOf(participant));
        assertThat(event.member().nickname()).isEqualTo("참가자");
        assertThat(event.status()).isEqualTo("ACTIVE");
    }

    @Test
    void 정원이_차는_참여도_방_상태는_ACTIVE_로_보낸다() {
        PartyResult party = service.open(createParty(host, 2));

        service.join(party.id(), participant);

        assertThat(signals.joined.getFirst().status()).as("정원이 차도 ACTIVE 다").isEqualTo("ACTIVE");
    }

    @Test
    void 유저_요약이_없는_사람이_참여해도_신호는_나간다() {
        PartyResult party = service.open(createParty(host, 3));

        service.join(party.id(), guest);

        assertThat(signals.joined.getFirst().member().publicId()).isNull();
    }

    @Test
    void 방을_만들_때는_신호를_보내지_않는다() {
        service.open(createParty(host, 3));

        assertThat(signals.sent).as("방금 만든 방은 구독자가 없다").isEmpty();
    }

    @Test
    void 사람이_남아_있는_방에서_나가면_나간_사람의_publicId_와_방_상태를_보낸다() {
        userAccess.등록(participant, "참가자");
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), participant);
        signals.sent.clear();

        service.leave(party.id(), participant);

        assertThat(signals.sent).containsExactly(party.id() + ":member-left:" + participant);
        MemberLeft event = signals.left.getFirst();
        assertThat(event.publicId()).isEqualTo(userAccess.publicIdOf(participant));
        assertThat(event.status()).isEqualTo("ACTIVE");
    }

    @Test
    void 정원이_찬_방에서_한_명이_나가도_상태는_ACTIVE_다() {
        PartyResult party = service.open(createParty(host, 2));
        service.join(party.id(), participant);

        service.leave(party.id(), participant);

        assertThat(signals.left.getFirst().status()).isEqualTo("ACTIVE");
    }

    @Test
    void 방장이_나가면_member_left에_승계된_방장이_실린다() {
        // 남은 사람 화면의 "방장" 표시가 퇴장 즉시 바뀌어야 한다 - 이벤트에 승계 결과를 실어 보낸다.
        // 승계자는 "남은 사람 중 가장 먼저 합류한 사람" - memberId 순서(participant=2 < guest=4)가 아니라 합류 순서를 따른다
        userAccess.등록(guest, "게스트");
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), guest);
        service.join(party.id(), participant);
        signals.sent.clear();

        service.leave(party.id(), host);

        assertThat(signals.left.getLast().hostPublicId())
                .isEqualTo(userAccess.publicIdOf(guest));
    }

    @Test
    void 일반_멤버가_나가면_member_left의_방장은_그대로다() {
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), participant);
        signals.sent.clear();

        service.leave(party.id(), participant);

        assertThat(signals.left.getLast().hostPublicId())
                .isEqualTo(userAccess.publicIdOf(host));
    }

    @Test
    void 나간_사람의_연결_닫기도_커밋_후에_한다() {
        // 나가기가 롤백됐는데 연결부터 닫으면, 아직 멤버인 사람이 방 변화를 못 받는다
        List<Runnable> deferred = new ArrayList<>();
        service = serviceWith((name, task) -> deferred.add(task));
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), participant);
        deferred.clear();

        service.leave(party.id(), participant);

        assertThat(signals.sent).isEmpty();
        deferred.forEach(Runnable::run);
        assertThat(signals.sent).containsExactly(party.id() + ":member-left:" + participant);
    }

    @Test
    void 나가기는_연결_닫기와_퇴장_알림을_신호_하나로_맡긴다() {
        // 한 메시지라야 받는 서버에서 연결 닫기가 알림보다 항상 먼저 실행된다
        List<Runnable> deferred = new ArrayList<>();
        service = serviceWith((name, task) -> deferred.add(task));
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), participant);
        deferred.clear();

        service.leave(party.id(), participant);

        assertThat(deferred).hasSize(1);
    }

    @Test
    void 마지막_멤버가_나가_취소되면_closed_신호만_보낸다() {
        PartyResult party = service.open(createParty(host, 3));

        service.leave(party.id(), host);

        assertThat(signals.sent)
                .as("닫힐 방이라 퇴장 알림은 내지 않는다")
                .containsExactly(party.id() + ":closed");
    }

    @Test
    void 마지막_멤버가_나가면_closed_에_CANCELED_가_실리고_version_은_오른다() {
        PartyResult party = service.open(createParty(host, 3));

        service.leave(party.id(), host);

        assertThat(signals.closed.getLast()).isEqualTo(PartyClosed.from("CANCELED"));
        assertThat(versionOf(party.id())).isEqualTo(1);
    }

    @Test
    void 신호마다_저장된_version_이_실리고_1씩_오른다() {
        userAccess.등록(guest, "게스트");
        PartyResult party = service.open(createParty(host, 3));

        service.join(party.id(), participant);
        assertThat(signals.joined.getLast().version()).isEqualTo(1).isEqualTo(versionOf(party.id()));

        service.join(party.id(), guest);
        assertThat(signals.joined.getLast().version()).isEqualTo(2).isEqualTo(versionOf(party.id()));

        service.leave(party.id(), guest);
        assertThat(signals.left.getLast().version()).isEqualTo(3).isEqualTo(versionOf(party.id()));

        service.confirm(party.id(), host, MeetingPlan.at(뒤(Duration.ofMinutes(30))));
        assertThat(signals.confirmed.getLast().version()).isEqualTo(4).isEqualTo(versionOf(party.id()));
        assertThat(service.getPartyDetail(party.id()).version()).isEqualTo(4);
    }

    @Test
    void 정원이_차도_참여_신호의_version_은_1만_오른다() {
        PartyResult party = service.open(createParty(host, 2));

        service.join(party.id(), participant);

        assertThat(versionOf(party.id())).isEqualTo(1);
        assertThat(signals.joined.getLast().version()).isEqualTo(1);
        assertThat(signals.sent).containsExactly(party.id() + ":member-joined");
    }

    @Test
    void 신호는_직접_내지_않고_커밋_후_실행기에_맡긴다() {
        List<Runnable> deferred = new ArrayList<>();
        service = serviceWith((name, task) -> deferred.add(task));
        PartyResult party = service.open(createParty(host, 3));

        service.join(party.id(), participant);

        assertThat(signals.sent).as("커밋 전에는 나가지 않는다 - 롤백되면 영영 안 나간다").isEmpty();
        deferred.forEach(Runnable::run);
        assertThat(signals.sent).containsExactly(party.id() + ":member-joined");
    }

    @Test
    void 참여에_실패하면_신호를_맡기지도_않는다() {
        List<Runnable> deferred = new ArrayList<>();
        service = serviceWith((name, task) -> deferred.add(task));

        assertThatThrownBy(() -> service.join(999L, participant)).isInstanceOf(BusinessException.class);

        assertThat(deferred).isEmpty();
    }

    // ───────────────────────── 자동 정리 스윕 ─────────────────────────

    @Test
    void 만든_지_1일이_지난_미확정_방만_자동_정리된다() {
        Long 오래된방 = saveUnconfirmedOld(Instant.now().minus(Duration.ofDays(2)));   // anotherHost·guest 가 여기 묶여 있다
        PartyResult 방금찬방 = service.open(createParty(host, 2));
        service.join(방금찬방.id(), participant);

        new CompletedPartySweeper(parties, service).sweep();

        assertThat(parties.findById(오래된방).orElseThrow().getStatus()).as("약속 없이 하루가 지났다 - 동승이 없었다").isEqualTo(PartyStatus.CANCELED);
        assertThat(parties.findById(방금찬방.id()).orElseThrow().getStatus()).as("1일 전이면 건드리지 않는다").isEqualTo(PartyStatus.ACTIVE);
    }

    @Test
    void 약속이_아직_지나지_않은_방은_만든_지_오래됐어도_자동_정리하지_않는다() {
        Long 약속전방 = saveConfirmedOld(Instant.now().plus(Duration.ofHours(1)), false);   // 만든 지 오래됐지만 약속은 1시간 뒤
        Long 미확정방 = saveUnconfirmedOld(Instant.now().minus(Duration.ofDays(2)));

        new CompletedPartySweeper(parties, service).sweep();

        assertThat(parties.findById(약속전방).orElseThrow().getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(parties.findById(미확정방).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @Test
    void 도착자가_있는_확정_방을_자동_정리하면_FINISHED_이고_하차_기록이_남는다() {
        Long 오래된방 = saveConfirmedOld(Instant.now().minus(Duration.ofDays(2)), true);

        service.autoClose(오래된방);

        assertThat(parties.findById(오래된방).orElseThrow().getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(events.closedFor(오래된방, "FINISHED")).isEqualTo(1);
        RideRecord rideRecord = rideRecords.findById(오래된방).orElseThrow();
        assertThat(rideRecord.getDropoffTime()).isNotNull();
        assertThat(rideRecord.getDeparturePlace()).isEqualTo("강남역");
        assertThat(rideRecord.getDestinationPlace()).isEqualTo("판교역");
    }

    @Test
    void 도착자가_없는_확정_방을_자동_정리하면_CANCELED_이고_하차_기록이_남지_않는다() {
        Long 오래된방 = saveConfirmedOld(Instant.now().minus(Duration.ofDays(2)), false);

        service.autoClose(오래된방);

        assertThat(parties.findById(오래된방).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(events.closedFor(오래된방, "CANCELED")).isEqualTo(1);
        assertThat(rideRecords.findById(오래된방)).isEmpty();
    }

    @Test
    void 자동_정리로_닫힌_뒤에는_같은_사람이_새_방을_만들_수_있다() {
        // 이게 안 되면 출발을 안 누른 방에 계정이 잠긴다
        Long 오래된방 = saveUnconfirmedOld(Instant.now().minus(Duration.ofDays(2)));

        service.autoClose(오래된방);

        PartyResult next = service.open(createParty(anotherHost, 3));

        assertThat(next.id()).isNotEqualTo(오래된방);
    }

    @Test
    void 자동_정리해도_closed_신호를_보낸다() {
        Long 오래된방 = saveUnconfirmedOld(Instant.now().minus(Duration.ofDays(2)));

        service.autoClose(오래된방);

        assertThat(signals.sent)
                .as("대기 화면에 남아 있는 사용자가 홈으로 돌아가야 한다")
                .containsExactly(오래된방 + ":closed");
    }

    @Test
    void 자동_정리하면_closed_에_상태가_실리고_version_은_오른다() {
        Long 오래된방 = saveUnconfirmedOld(Instant.now().minus(Duration.ofDays(2)));

        service.autoClose(오래된방);

        assertThat(signals.closed.getLast()).isEqualTo(PartyClosed.from("CANCELED"));
        assertThat(versionOf(오래된방)).isEqualTo(1);
    }

    @Test
    void 이미_닫힌_방은_자동_정리가_아무것도_하지_않는다() {
        PartyResult party = service.open(createParty(host, 2));
        service.leave(party.id(), host);   // CANCELED
        signals.sent.clear();

        service.autoClose(party.id());   // 예외 없이 건너뛴다

        assertThat(parties.findById(party.id()).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(signals.sent).isEmpty();
        assertThat(versionOf(party.id())).isEqualTo(1);
    }

    @Test
    void 매칭중인_유저는_새_방을_만들_수_없다() {
        // 정원이 찬 방(ACTIVE 그대로)에도 묶여 있어야 한다
        PartyResult party = service.open(createParty(host, 2));
        service.join(party.id(), participant);   // 정원 충족

        assertThatThrownBy(() -> service.open(createParty(participant, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
    }

    // ───────────────────────── 참여 응답 ─────────────────────────

    @Test
    void 참여_응답은_상태와_현재_인원을_돌려준다() {
        PartyResult party = service.open(createParty(host, 3));

        PartyStatusResponse joined = service.join(party.id(), participant);

        assertThat(joined.status()).isEqualTo("ACTIVE");
        assertThat(joined.currentMembers()).isEqualTo(2);
    }

    @Test
    void 정원이_차도_참여_응답의_상태는_ACTIVE_다() {
        PartyResult party = service.open(createParty(host, 2));

        PartyStatusResponse joined = service.join(party.id(), participant);

        assertThat(joined.status()).isEqualTo("ACTIVE");
        assertThat(joined.currentMembers()).isEqualTo(2);
    }

    // ───────────────────────── 지도 영역(뷰포트) 조회 ─────────────────────────

    @Test
    void 영역_안에서_출발하는_방만_조회된다() {
        service.open(createPartyAt(host, 37.4979, 127.0276, 3));        // 강남역 - 영역 안
        service.open(createPartyAt(anotherHost, 37.5665, 126.9780, 3)); // 시청 - 영역 밖

        List<PartySummary> result = service.findActivePartiesWithin(37.49, 127.02, 37.51, 127.06);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).departure()).isEqualTo("강남역");
    }

    @Test
    void 영역_모서리에_정확히_걸친_방도_조회된다() {
        // between은 양끝 포함 - 경계 좌표가 빠지면 화면 가장자리의 마커가 사라진다
        service.open(createPartyAt(host, 37.49, 127.02, 3));

        assertThat(service.findActivePartiesWithin(37.49, 127.02, 37.51, 127.06)).hasSize(1);
    }

    @Test
    void 영역_안이어도_정원이_찬_방은_조회되지_않는다() {
        // 정원이 차도 ACTIVE 지만 들어갈 수 없는 방 - 지도에 마커가 남아 있으면 못 타는 방을 탭하게 된다
        PartyResult party = service.open(createPartyAt(host, 37.4979, 127.0276, 2));
        service.join(party.id(), participant);   // 정원 충족

        assertThat(service.findActivePartiesWithin(37.49, 127.02, 37.51, 127.06)).isEmpty();
    }

    @Test
    void 목록의_현재_인원은_참여한_사람_수다() {
        PartyResult party = service.open(createPartyAt(host, 37.4979, 127.0276, 3));
        service.join(party.id(), participant);

        List<PartySummary> result = service.findActivePartiesWithin(37.49, 127.02, 37.51, 127.06);

        assertThat(result).singleElement()
                .satisfies(s -> {
                    assertThat(s.currentMembers()).isEqualTo(2L);
                    assertThat(s.capacity()).isEqualTo(3);
                });
    }

    @Test
    void 영역_안의_방이_상한을_넘으면_최신_방부터_상한까지만_돌려준다() {
        // 상한이 없으면 방이 쌓일수록 목록 쿼리가 느려져 DB 를 독점한다(부하테스트에서 DB 시간의 대부분이 이 쿼리였다)
        Long last = null;
        for(long creator = 1000; creator < 1000 + PartyService.MAP_LIST_LIMIT + 5; creator++) {
            last = service.open(createPartyAt(creator, 37.4979, 127.0276, 3)).id();
        }

        List<PartySummary> result = service.findActivePartiesWithin(37.49, 127.02, 37.51, 127.06);

        assertThat(result).hasSize(PartyService.MAP_LIST_LIMIT);
        assertThat(result.get(0).id()).isEqualTo(last);
    }

    @Test
    void 뒤집힌_영역_좌표로는_조회할_수_없다() {
        // 남서가 북동보다 크면 between 결과가 항상 빈 목록 - 프론트 좌표 순서 실수를 조용히 삼키지 않고 알려준다
        assertThatThrownBy(() -> service.findActivePartiesWithin(37.51, 127.06, 37.49, 127.02))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_MAP_BOUNDS);
    }

    // ───────────────────────── 헬퍼 ─────────────────────────

    private OpenPartyCommand createPartyAt(Long creatorId, double lat, double lng, int capacity) {
        return new OpenPartyCommand(creatorId, lat, lng, 37.3948, 127.1112,
                "강남역", "판교역", capacity);
    }

    private long versionOf(Long partyId) {
        return parties.findById(partyId).orElseThrow().getVersion();
    }

    private OpenPartyCommand createParty(Long creatorId, int capacity) {
        return new OpenPartyCommand(creatorId, 37.4979, 127.0276, 37.3948, 127.1112,
                "강남역", "판교역", capacity);
    }

    /** anotherHost·guest 가 든 정원 2 의 미확정 실시간 방(ACTIVE)을 원하는 생성 시각으로 저장 - 도메인은 지금 시각만 찍으므로 과거 시각은 load 로만 만들 수 있다 */
    private Long saveUnconfirmedOld(Instant createdAt) {
        Party party = Party.load(null, 강남역(), 판교역(), "강남역", "판교역", new Capacity(2),
                members(createdAt, false), anotherHost, createdAt, PartyStatus.ACTIVE, 12000, 25, "_p~iF~ps|U_ulLnnqC", 0L,
                RoomType.REALTIME, null, null, null, 0);
        return parties.save(party).getId();
    }

    /** anotherHost·guest 가 든 정원 2 의 확정 실시간 방(COMPLETED). arrived 면 둘 다 약속 시각에 도착했다 */
    private Long saveConfirmedOld(Instant departureAt, boolean arrived) {
        Instant createdAt = Instant.now().minus(Duration.ofDays(2));
        Party party = Party.load(null, 강남역(), 판교역(), "강남역", "판교역", new Capacity(2),
                members(createdAt, arrived ? departureAt : null), anotherHost, createdAt, PartyStatus.COMPLETED, 12000, 25, "_p~iF~ps|U_ulLnnqC", 0L,
                RoomType.REALTIME, departureAt, null, null, 0);
        return parties.save(party).getId();
    }

    /** memberIds 가 합류 순으로 탄 끝난(FINISHED) 방 - 탑승 횟수의 근거 */
    private Long saveFinishedRide(Long... memberIds) {
        Instant createdAt = Instant.now().minus(Duration.ofDays(1));
        List<PartyMember> members = new ArrayList<>();
        for(int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], createdAt.plusSeconds(i), createdAt.plus(Duration.ofMinutes(30))));
        }
        Party party = Party.load(null, 강남역(), 판교역(), "강남역", "판교역", new Capacity(memberIds.length),
                members, memberIds[0], createdAt, PartyStatus.FINISHED, 12000, 25, "_p~iF~ps|U_ulLnnqC", 0L,
                RoomType.REALTIME, createdAt.plus(Duration.ofMinutes(30)), null, null, 0);
        return parties.save(party).getId();
    }

    private List<PartyMember> members(Instant createdAt, boolean arrived) {
        return members(createdAt, arrived ? createdAt.plus(Duration.ofMinutes(30)) : null);
    }

    private List<PartyMember> members(Instant createdAt, Instant arrivedAt) {
        return List.of(PartyMember.load(anotherHost, createdAt, arrivedAt), PartyMember.load(guest, createdAt.plusSeconds(1), arrivedAt));
    }

    private Location 강남역() {
        return new Location(37.4979, 127.0276);
    }

    private Location 판교역() {
        return new Location(37.3948, 127.1112);
    }

    /** 트랜잭션이 없는 단위 테스트용 - 맡긴 작업을 그 자리에서 실행한다 */
    private static final AfterCommitExecutor IMMEDIATE = (name, task) -> task.run();

    /** 나간 SSE 신호를 "partyId:이름" 으로 기록하고, 실린 내용은 따로 모은다 */
    static class RecordingPartyChangeNotifier implements PartyChangeNotifier {
        final List<String> sent = new ArrayList<>();
        final List<MemberJoined> joined = new ArrayList<>();
        final List<MemberLeft> left = new ArrayList<>();
        final List<PartyClosed> closed = new ArrayList<>();
        final List<PartyConfirmed> confirmed = new ArrayList<>();
        final List<MemberArrived> arrived = new ArrayList<>();

        @Override
        public void confirmed(Long partyId, PartyConfirmed event) {
            sent.add(partyId + ":confirmed");
            confirmed.add(event);
        }

        @Override
        public void memberArrived(Long partyId, MemberArrived event) {
            sent.add(partyId + ":member-arrived");
            arrived.add(event);
        }

        @Override
        public void memberJoined(Long partyId, MemberJoined event) {
            sent.add(partyId + ":member-joined");
            joined.add(event);
        }

        @Override
        public void memberLeft(Long partyId, Long memberId, MemberLeft event) {
            sent.add(partyId + ":member-left:" + memberId);
            left.add(event);
        }

        @Override
        public void closed(Long partyId, PartyClosed event) {
            sent.add(partyId + ":closed");
            closed.add(event);
        }
    }

    /** 발행된 이벤트를 기록하는 가짜 발행기 */
    static class RecordingEventPublisher implements ApplicationEventPublisher {
        private final List<Object> published = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            published.add(event);
        }

        long joinedFor(Long partyId, Long memberId) {
            return published.stream()
                    .filter(e -> e instanceof PartyMemberJoinedEvent j && j.partyId().equals(partyId) && j.memberId().equals(memberId))
                    .count();
        }

        long closedFor(Long partyId, String status) {
            return published.stream()
                    .filter(e -> e instanceof PartyClosedEvent c && c.partyId().equals(partyId) && c.status().equals(status))
                    .count();
        }

        long leftFor(Long partyId, Long memberId) {
            return published.stream()
                    .filter(e -> e instanceof PartyMemberLeftEvent l && l.partyId().equals(partyId) && l.memberId().equals(memberId))
                    .count();
        }
    }

    /** 도보 경로 가짜 (OSRM 미호출) - 입력 좌표마다 1분을 같은 순서로 반환 */
    static class FakeWalkRouteFinder implements WalkRouteFinder {
        @Override
        public List<WalkingEstimate> fromTo(Coordinate from, List<Coordinate> to) {
            return to.stream().map(c -> new WalkingEstimate(60)).toList();
        }

        @Override
        public List<WalkingEstimate> toFrom(Coordinate to, List<Coordinate> from) {
            return from.stream().map(c -> new WalkingEstimate(60)).toList();
        }
    }
}
