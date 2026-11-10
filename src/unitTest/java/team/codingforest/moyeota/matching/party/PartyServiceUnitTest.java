package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.matching.api.dto.PartyClosedEvent;
import team.codingforest.moyeota.matching.api.dto.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.api.dto.PartyMemberLeftEvent;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingPlan;
import team.codingforest.moyeota.matching.party.domain.NearbyReservation;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyChangeNotifier;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PartyServiceUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long THIRD_ID = 3L;
    private static final UUID HOST_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final double DEPARTURE_LAT = 37.4979;
    private static final double DEPARTURE_LNG = 127.0276;
    private static final double DESTINATION_LAT = 37.3948;
    private static final double DESTINATION_LNG = 127.1112;
    private static final Location DEPARTURE = new Location(DEPARTURE_LAT, DEPARTURE_LNG);
    private static final Location DESTINATION = new Location(DESTINATION_LAT, DESTINATION_LNG);
    // 도착 요청 좌표 - 출발지에서 북쪽으로 약 50m / 약 111m. 반경 안인지는 DB(PostGIS)가 재므로 여기선 isNearDeparture 를 mock 한다
    private static final double NEAR_LAT = DEPARTURE_LAT + 0.00045;
    private static final double FAR_LAT = DEPARTURE_LAT + 0.001;
    // 도착을 받는 출발지 반경(Party.arrivalRadiusMeters) - 값이 바뀌면 이 테스트가 알아채도록 숫자로 적는다
    private static final double ARRIVAL_RADIUS = 100.0;
    private static final double RESERVATION_ARRIVAL_RADIUS = 400.0;
    private static final Instant BASE_TIME = Instant.parse("2026-10-01T00:00:00Z");
    private static final RouteEstimate ESTIMATE = new RouteEstimate(12000, 25, "encoded-path");
    private static final String SSE_TASK = "party.sse";

    @Mock
    private Parties parties;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private RouteService routeService;
    @Mock
    private UserAccess userAccess;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private PartyChangeNotifier partyChangeNotifier;
    @Mock
    private WalkRouteFinder walkRouteFinder;
    @Mock
    private RideRecords rideRecords;
    @Mock
    private PartyOpener partyOpener;

    @InjectMocks
    private PartyService partyService;

    // ───────────────────────── open ─────────────────────────

    @DisplayName("open - 진행 중인 방을 확인하고 경로를 조회한 뒤 그 결과로 PartyOpener 에 저장을 맡긴다")
    @Test
    void create() {

        /*
         * settings
         */
        OpenPartyCommand command = createCommand(3);
        PartyResult opened = PartyResult.from(party(PartyStatus.ACTIVE, 3, 0L, HOST_ID));

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(HOST_ID));
        doReturn(ESTIMATE).when(routeService).estimate(eq(DEPARTURE_LAT), eq(DEPARTURE_LNG), eq(DESTINATION_LAT), eq(DESTINATION_LNG));
        doReturn(opened).when(partyOpener).open(eq(command), eq(ESTIMATE));

        /*
         * test
         */
        PartyResult result = partyService.create(command);

        /*
         * verify
         */
        assertThat(result).isSameAs(opened);
        InOrder order = inOrder(parties, routeService, partyOpener);
        order.verify(parties).existsOngoingRealtimeByMemberId(eq(HOST_ID));
        order.verify(routeService).estimate(eq(DEPARTURE_LAT), eq(DEPARTURE_LNG), eq(DESTINATION_LAT), eq(DESTINATION_LNG));
        order.verify(partyOpener).open(eq(command), eq(ESTIMATE));
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("open - 진행 중인 방이 있으면 ALREADY_JOINED_OTHER_PARTY 이고 경로 조회도 하지 않는다")
    @Test
    void create_alreadyInOngoingParty() {

        /*
         * settings
         */
        doReturn(true).when(parties).existsOngoingRealtimeByMemberId(eq(HOST_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.create(createCommand(3)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
        verifyNoInteractions(routeService, partyOpener);
    }

    @DisplayName("open - 경로 조회가 실패하면 예외가 그대로 나가고 저장을 맡기지 않는다")
    @Test
    void create_routeSearchFailed() {

        /*
         * settings
         */
        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(HOST_ID));
        doThrow(new BusinessException(MatchingErrorCode.ROUTE_SEARCH_FAILED))
                .when(routeService).estimate(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.create(createCommand(3)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ROUTE_SEARCH_FAILED);
        verifyNoInteractions(partyOpener);
    }

    @DisplayName("open - 저장 단계의 예외는 그대로 나간다")
    @Test
    void create_openerFailed() {

        /*
         * settings
         */
        OpenPartyCommand command = createCommand(3);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(HOST_ID));
        doReturn(ESTIMATE).when(routeService).estimate(eq(DEPARTURE_LAT), eq(DEPARTURE_LNG), eq(DESTINATION_LAT), eq(DESTINATION_LNG));
        doThrow(new BusinessException(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY)).when(partyOpener).open(eq(command), eq(ESTIMATE));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.create(command));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
    }

    /** 네이버 호출 동안 DB 커넥션을 쥐고 있으면 안 된다 - 트랜잭션은 PartyOpener 에만 있어야 한다 */
    @DisplayName("open - 경로 조회가 있는 바깥 메서드에는 트랜잭션이 없다")
    @Test
    void create_notTransactional() throws Exception {

        /*
         * test
         */
        boolean transactional = PartyService.class.getMethod("create", OpenPartyCommand.class)
                .isAnnotationPresent(Transactional.class);

        /*
         * verify
         */
        assertThat(transactional).isFalse();
    }

    // ───────────────────────── reserve ─────────────────────────

    @DisplayName("reserve - PartyOpener 에 맡기고 그 결과를 그대로 돌려준다")
    @Test
    void reserve() {

        /*
         * settings
         */
        OpenReservationCommand command = reservationCommand();
        PartyResult reserved = PartyResult.from(party(RoomType.RESERVED, command.departureAt(), PartyStatus.ACTIVE, 3, 0L, HOST_ID));
        doReturn(reserved).when(partyOpener).reserve(eq(command));

        /*
         * test
         */
        PartyResult result = partyService.reserve(command);

        /*
         * verify
         */
        assertThat(result).isSameAs(reserved);
        verify(partyOpener).reserve(eq(command));
        verifyNoInteractions(parties, routeService, eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    // ───────────────────────── join ─────────────────────────

    @DisplayName("join - 정원이 남으면 저장 결과로 응답하고 커밋 후 참여 신호를 보낸다")
    @Test
    void join() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 3L, HOST_ID);
        Party saved = party(PartyStatus.ACTIVE, 3, 4L, HOST_ID, MEMBER_ID);
        MemberSummary summary = new MemberSummary(MEMBER_PUBLIC_ID, "참가자", "image.png");

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(Map.of(MEMBER_ID, summary)).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of(MEMBER_ID, 5)).when(parties).countFinishedRides(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(saved).when(parties).save(eq(party));

        /*
         * test
         */
        PartyStatusResponse response = partyService.join(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(response).isEqualTo(new PartyStatusResponse("ACTIVE", 2));
        assertThat(party.isMember(MEMBER_ID)).isTrue();
        verify(eventPublisher).publishEvent(eq(new PartyMemberJoinedEvent(PARTY_ID, MEMBER_ID)));

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();

        PartyDetailResult.MemberInfo info = new PartyDetailResult.MemberInfo(MEMBER_PUBLIC_ID, "참가자", "image.png", 5, false, false);
        verify(partyChangeNotifier).memberJoined(eq(PARTY_ID), eq(new MemberJoined(info, "ACTIVE", 4L)));
    }

    @DisplayName("join - 정원이 차도 상태는 ACTIVE 그대로이고 ACTIVE 로 신호를 보낸다")
    @Test
    void join_becomesFull() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 2, 0L, HOST_ID);
        Party saved = party(PartyStatus.ACTIVE, 2, 1L, HOST_ID, MEMBER_ID);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(saved).when(parties).save(eq(party));

        /*
         * test
         */
        PartyStatusResponse response = partyService.join(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(party.isFull()).isTrue();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        verify(parties).save(eq(party));
        assertThat(response).isEqualTo(new PartyStatusResponse("ACTIVE", 2));

        capturedAfterCommitTask().run();
        ArgumentCaptor<MemberJoined> captor = ArgumentCaptor.forClass(MemberJoined.class);
        verify(partyChangeNotifier).memberJoined(eq(PARTY_ID), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().version()).isEqualTo(1L);
    }

    @DisplayName("join - 유저 요약이 없어도 참여되고 신호의 멤버 정보는 null, 탑승 횟수는 0 이다")
    @Test
    void join_noMemberSummary() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 0L, HOST_ID);
        Party saved = party(PartyStatus.ACTIVE, 3, 1L, HOST_ID, MEMBER_ID);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(saved).when(parties).save(eq(party));

        /*
         * test
         */
        partyService.join(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        capturedAfterCommitTask().run();
        PartyDetailResult.MemberInfo info = new PartyDetailResult.MemberInfo(null, null, null, 0, false, false);
        verify(partyChangeNotifier).memberJoined(eq(PARTY_ID), eq(new MemberJoined(info, "ACTIVE", 1L)));
    }

    @DisplayName("join - 신호의 host 는 고정값이 아니라 저장 결과의 방장 판별을 따른다")
    @Test
    void join_hostFromSaved() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 0L, HOST_ID);
        Party saved = party(PartyStatus.ACTIVE, 3, 1L, MEMBER_ID, HOST_ID);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(saved).when(parties).save(eq(party));

        /*
         * test
         */
        partyService.join(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        capturedAfterCommitTask().run();
        PartyDetailResult.MemberInfo info = new PartyDetailResult.MemberInfo(null, null, null, 0, true, false);
        verify(partyChangeNotifier).memberJoined(eq(PARTY_ID), eq(new MemberJoined(info, "ACTIVE", 1L)));
    }

    @DisplayName("join - 진행 중인 방이 있으면 ALREADY_JOINED_OTHER_PARTY 이고 뒤 단계로 가지 않는다")
    @Test
    void join_alreadyInOngoingParty() {

        /*
         * settings
         */
        doReturn(true).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.join(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
        verifyNoInteractions(userAccess, eventPublisher, afterCommitExecutor);
        verify(parties, never()).findByIdForUpdate(any());
        verify(parties, never()).save(any());
    }

    @DisplayName("join - 사용자 잠금을 먼저 잡고 방을 잠근다")
    @Test
    void join_locksMemberBeforeParty() {

        /*
         * settings
         */
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(List.of(MEMBER_ID)));
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        catchThrowable(() -> partyService.join(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        InOrder order = inOrder(parties);
        order.verify(parties).lockMember(MEMBER_ID);
        order.verify(parties).findByIdForUpdate(PARTY_ID);
    }

    @DisplayName("join - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void join_partyNotFound() {

        /*
         * settings
         */
        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(List.of(MEMBER_ID)));
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.join(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor);
    }

    @DisplayName("join - 모집 중이 아닌 방이면 PARTY_CLOSED")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void join_partyClosed(PartyStatus status) {

        /*
         * settings
         */
        Party party = party(status, 3, 0L, HOST_ID);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.join(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_CLOSED);
        assertThat(party.isMember(MEMBER_ID)).isFalse();
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor);
    }

    @DisplayName("join - 정원이 찬 ACTIVE 방이면 PARTY_FULL")
    @Test
    void join_partyFull() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 2, 0L, HOST_ID, THIRD_ID);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.join(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_FULL);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor);
    }

    @DisplayName("join - 이미 그 방의 멤버면 ALREADY_JOINED_PARTY")
    @Test
    void join_alreadyMember() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.join(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_JOINED_PARTY);
        assertThat(party.getMembers()).hasSize(2);
        verify(parties, never()).save(any());
    }

    // ───────────────────────── leave ─────────────────────────

    @DisplayName("leave - 일반 멤버가 나가면 방장은 그대로이고 커밋 후 퇴장 신호를 보낸다")
    @Test
    void leave() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 2L, HOST_ID, MEMBER_ID);

        doReturn(Map.of(MEMBER_ID, summary(MEMBER_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of(HOST_ID, summary(HOST_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(HOST_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.leave(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(party.isMember(MEMBER_ID)).isFalse();
        verify(parties).save(eq(party));
        verify(eventPublisher).publishEvent(eq(new PartyMemberLeftEvent(PARTY_ID, MEMBER_ID)));
        verify(eventPublisher, never()).publishEvent(any(PartyClosedEvent.class));

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();

        verify(partyChangeNotifier).memberLeft(eq(PARTY_ID), eq(MEMBER_ID), eq(new MemberLeft(MEMBER_PUBLIC_ID, "ACTIVE", HOST_PUBLIC_ID, 3L)));
        verify(partyChangeNotifier, never()).closed(any(), any());
    }

    @DisplayName("leave - 방장이 나가면 다음으로 일찍 합류한 멤버가 신호의 방장으로 실린다")
    @Test
    void leave_hostSuccession() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID, THIRD_ID);

        doReturn(Map.of(HOST_ID, summary(HOST_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(HOST_ID)));
        doReturn(Map.of(MEMBER_ID, summary(MEMBER_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.leave(PARTY_ID, HOST_ID);

        /*
         * verify
         */
        capturedAfterCommitTask().run();
        verify(partyChangeNotifier).memberLeft(eq(PARTY_ID), eq(HOST_ID), eq(new MemberLeft(HOST_PUBLIC_ID, "ACTIVE", MEMBER_PUBLIC_ID, 1L)));
    }

    @DisplayName("leave - 약속을 확정한 실시간 방에서도 나갈 수 있고 2명 이상 남으면 COMPLETED 그대로 퇴장 신호를 보낸다")
    @Test
    void leave_confirmedRealtime() {

        /*
         * settings
         */
        Party party = party(RoomType.REALTIME, Instant.now().plus(Duration.ofMinutes(30)), PartyStatus.COMPLETED, 3, 3L, HOST_ID, MEMBER_ID, THIRD_ID);

        doReturn(Map.of(MEMBER_ID, summary(MEMBER_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of(HOST_ID, summary(HOST_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(HOST_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.leave(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getMembers()).hasSize(2);
        verify(parties).save(eq(party));
        verify(eventPublisher, never()).publishEvent(any(PartyClosedEvent.class));

        capturedAfterCommitTask().run();
        verify(partyChangeNotifier).memberLeft(eq(PARTY_ID), eq(MEMBER_ID), eq(new MemberLeft(MEMBER_PUBLIC_ID, "COMPLETED", HOST_PUBLIC_ID, 4L)));
        verify(partyChangeNotifier, never()).closed(any(), any());
    }

    @DisplayName("leave - 약속을 확정한 실시간 방에서 나가 혼자 남으면 CANCELED 로 닫히고 퇴장 신호 대신 closed 를 보낸다")
    @Test
    void leave_confirmedLeavesOneAlone() {

        /*
         * settings
         */
        Party party = party(RoomType.REALTIME, Instant.now().plus(Duration.ofMinutes(30)), PartyStatus.COMPLETED, 3, 3L, HOST_ID, MEMBER_ID);

        doReturn(Map.of(MEMBER_ID, summary(MEMBER_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.leave(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.getMembers()).extracting(PartyMember::getMemberId).containsExactly(HOST_ID);
        verify(parties).save(eq(party));
        verify(userAccess, times(1)).findMemberSummaries(any());
        verify(eventPublisher).publishEvent(eq(new PartyMemberLeftEvent(PARTY_ID, MEMBER_ID)));
        verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, "CANCELED")));

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();

        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("CANCELED")));
        verify(partyChangeNotifier, never()).memberLeft(any(), any(), any());
    }

    @DisplayName("leave - 예약 방은 나갈 수 있고 혼자 남아도 ACTIVE 그대로 퇴장 신호를 보낸다")
    @Test
    void leave_reservation() {

        /*
         * settings
         */
        Party party = party(RoomType.RESERVED, Instant.now().plus(Duration.ofDays(1)), PartyStatus.ACTIVE, 3, 1L, HOST_ID, MEMBER_ID);

        doReturn(Map.of(MEMBER_ID, summary(MEMBER_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of(HOST_ID, summary(HOST_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(HOST_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.leave(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.getMembers()).extracting(PartyMember::getMemberId).containsExactly(HOST_ID);
        verify(parties).save(eq(party));
        verify(eventPublisher, never()).publishEvent(any(PartyClosedEvent.class));

        capturedAfterCommitTask().run();
        verify(partyChangeNotifier).memberLeft(eq(PARTY_ID), eq(MEMBER_ID), eq(new MemberLeft(MEMBER_PUBLIC_ID, "ACTIVE", HOST_PUBLIC_ID, 2L)));
        verify(partyChangeNotifier, never()).closed(any(), any());
    }

    @DisplayName("leave - 마지막 멤버가 나가면 CANCELED 로 닫히고 퇴장 신호와 방장 조회 없이 closed 만 보낸다")
    @Test
    void leave_lastMember() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 0L, HOST_ID);

        doReturn(Map.of(HOST_ID, summary(HOST_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(HOST_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.leave(PARTY_ID, HOST_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        verify(parties).save(eq(party));
        verify(userAccess, times(1)).findMemberSummaries(any());
        verify(eventPublisher).publishEvent(eq(new PartyMemberLeftEvent(PARTY_ID, HOST_ID)));
        verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, "CANCELED")));

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();

        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("CANCELED")));
        verify(partyChangeNotifier, never()).memberLeft(any(), any(), any());
    }

    @DisplayName("leave - 나가는 사람의 유저 요약이 없으면 신호의 publicId 가 null 이다")
    @Test
    void leave_noLeaverSummary() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID);

        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of(HOST_ID, summary(HOST_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(HOST_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.leave(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        capturedAfterCommitTask().run();
        verify(partyChangeNotifier).memberLeft(eq(PARTY_ID), eq(MEMBER_ID), eq(new MemberLeft(null, "ACTIVE", HOST_PUBLIC_ID, 1L)));
    }

    @DisplayName("leave - 방장의 유저 요약이 없으면 신호의 hostPublicId 가 null 이다")
    @Test
    void leave_noHostSummary() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID);

        doReturn(Map.of(MEMBER_ID, summary(MEMBER_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(HOST_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.leave(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        capturedAfterCommitTask().run();
        verify(partyChangeNotifier).memberLeft(eq(PARTY_ID), eq(MEMBER_ID), eq(new MemberLeft(MEMBER_PUBLIC_ID, "ACTIVE", null, 1L)));
    }

    @DisplayName("leave - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void leave_partyNotFound() {

        /*
         * settings
         */
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.leave(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor);
    }

    @DisplayName("leave - 멤버가 아니면 NOT_PARTY_MEMBER")
    @Test
    void leave_notMember() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 0L, HOST_ID);

        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.leave(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_PARTY_MEMBER);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor);
    }

    @DisplayName("leave - 닫힌 방이면 PARTY_CLOSED 이고 저장·이벤트·신호가 없다")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
    void leave_partyClosed(PartyStatus status) {

        /*
         * settings
         */
        Party party = party(status, 2, 0L, HOST_ID, MEMBER_ID);

        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.leave(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_CLOSED);
        assertThat(party.isMember(MEMBER_ID)).isTrue();
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    // ───────────────────────── getPartyDetail ─────────────────────────

    @DisplayName("getPartyDetail - 멤버별 요약·탑승 횟수·방장 표시를 합쳐 돌려준다")
    @Test
    void getPartyDetail() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 7L, HOST_ID, MEMBER_ID);
        List<Long> memberIds = List.of(HOST_ID, MEMBER_ID);

        doReturn(Optional.of(party)).when(parties).findById(eq(PARTY_ID));
        doReturn(Map.of(HOST_ID, new MemberSummary(HOST_PUBLIC_ID, "방장", "host.png"),
                MEMBER_ID, new MemberSummary(MEMBER_PUBLIC_ID, "참가자", "member.png")))
                .when(userAccess).findMemberSummaries(eq(memberIds));
        doReturn(Map.of(HOST_ID, 4)).when(parties).countFinishedRides(eq(memberIds));

        /*
         * test
         */
        PartyDetailResult result = partyService.getPartyDetail(PARTY_ID);

        /*
         * verify
         */
        assertThat(result.id()).isEqualTo(PARTY_ID);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.capacity()).isEqualTo(3);
        assertThat(result.currentMembers()).isEqualTo(2);
        assertThat(result.version()).isEqualTo(7L);
        assertThat(result.roomType()).isEqualTo("REALTIME");
        assertThat(result.departureAt()).isNull();
        assertThat(result.confirmed()).isFalse();
        assertThat(result.members()).containsExactly(
                new PartyDetailResult.MemberInfo(HOST_PUBLIC_ID, "방장", "host.png", 4, true, false),
                new PartyDetailResult.MemberInfo(MEMBER_PUBLIC_ID, "참가자", "member.png", 0, false, false));
    }

    @DisplayName("getPartyDetail - 유저 요약이 없는 멤버는 표시 정보가 null 이어도 목록에 남는다")
    @Test
    void getPartyDetail_noMemberSummary() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 0L, HOST_ID);

        doReturn(Optional.of(party)).when(parties).findById(eq(PARTY_ID));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(HOST_ID)));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(List.of(HOST_ID)));

        /*
         * test
         */
        PartyDetailResult result = partyService.getPartyDetail(PARTY_ID);

        /*
         * verify
         */
        assertThat(result.members()).containsExactly(new PartyDetailResult.MemberInfo(null, null, null, 0, true, false));
    }

    @DisplayName("getPartyDetail - 없는 방이면 PARTY_NOT_FOUND 이고 유저 조회를 하지 않는다")
    @Test
    void getPartyDetail_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.getPartyDetail(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(userAccess);
        verify(parties, never()).countFinishedRides(any());
    }

    @DisplayName("getPartyDetail - 약속을 확정한 실시간 방은 약속 시간과 confirmed=true 를 돌려준다")
    @Test
    void getPartyDetail_confirmedRealtime() {

        /*
         * settings
         */
        Instant departureAt = Instant.parse("2026-11-03T12:30:00Z");
        Party party = party(RoomType.REALTIME, departureAt, PartyStatus.COMPLETED, 3, 3L, HOST_ID, MEMBER_ID);
        List<Long> memberIds = List.of(HOST_ID, MEMBER_ID);

        doReturn(Optional.of(party)).when(parties).findById(eq(PARTY_ID));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(memberIds));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(memberIds));

        /*
         * test
         */
        PartyDetailResult result = partyService.getPartyDetail(PARTY_ID);

        /*
         * verify
         */
        assertThat(result.roomType()).isEqualTo("REALTIME");
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.departureAt()).isEqualTo(departureAt);
        assertThat(result.confirmed()).isTrue();
    }

    @DisplayName("getPartyDetail - 예약 방은 만들 때부터 confirmed=true 다")
    @Test
    void getPartyDetail_reservation() {

        /*
         * settings
         */
        Instant departureAt = Instant.parse("2026-11-04T08:00:00Z");
        Party party = party(RoomType.RESERVED, departureAt, PartyStatus.ACTIVE, 3, 0L, HOST_ID);

        doReturn(Optional.of(party)).when(parties).findById(eq(PARTY_ID));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(HOST_ID)));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(List.of(HOST_ID)));

        /*
         * test
         */
        PartyDetailResult result = partyService.getPartyDetail(PARTY_ID);

        /*
         * verify
         */
        assertThat(result.roomType()).isEqualTo("RESERVED");
        assertThat(result.departureAt()).isEqualTo(departureAt);
        assertThat(result.confirmed()).isTrue();
    }

    @DisplayName("getPartyDetail - 출발지에 도착한 멤버만 arrived=true 다")
    @Test
    void getPartyDetail_arrived() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.parse("2026-11-03T12:00:00Z"), PartyStatus.COMPLETED, 3L,
                waitingMember(HOST_ID, 0), arrivedMember(MEMBER_ID, 1));
        List<Long> memberIds = List.of(HOST_ID, MEMBER_ID);

        doReturn(Optional.of(party)).when(parties).findById(eq(PARTY_ID));
        doReturn(Map.of(HOST_ID, summary(HOST_PUBLIC_ID), MEMBER_ID, summary(MEMBER_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(memberIds));
        doReturn(Map.of()).when(parties).countFinishedRides(eq(memberIds));

        /*
         * test
         */
        PartyDetailResult result = partyService.getPartyDetail(PARTY_ID);

        /*
         * verify
         */
        assertThat(result.members()).extracting(PartyDetailResult.MemberInfo::publicId).containsExactly(HOST_PUBLIC_ID, MEMBER_PUBLIC_ID);
        assertThat(result.members()).extracting(PartyDetailResult.MemberInfo::arrived).containsExactly(false, true);
        assertThat(result.members()).extracting(PartyDetailResult.MemberInfo::host).containsExactly(true, false);
    }

    // ───────────────────────── findActivePartiesWithin ─────────────────────────

    @DisplayName("findActivePartiesWithin - ACTIVE 와 상한 100 을 붙여 위도끼리·경도끼리 묶어 조회한다")
    @Test
    void findActivePartiesWithin() {

        /*
         * settings
         */
        List<PartySummary> summaries = List.of(new PartySummary(PARTY_ID, "강남역", "판교역", 2L, 3, PartyStatus.ACTIVE, DEPARTURE_LAT, DEPARTURE_LNG));

        doReturn(summaries).when(parties).findSummariesWithinBounds(eq(PartyStatus.ACTIVE), eq(37.0), eq(38.0), eq(127.0), eq(128.0), eq(100));

        /*
         * test
         */
        List<PartySummary> result = partyService.findActivePartiesWithin(37.0, 127.0, 38.0, 128.0);

        /*
         * verify
         */
        assertThat(result).isEqualTo(summaries);
    }

    @DisplayName("findActivePartiesWithin - 남서가 북동 이상이면 INVALID_MAP_BOUNDS 이고 조회하지 않는다")
    @ParameterizedTest(name = "sw=({0}, {1}) ne=({2}, {3})")
    @CsvSource({
            "38.0, 127.0, 37.0, 128.0",
            "37.0, 128.0, 38.0, 127.0",
            "37.0, 127.0, 37.0, 128.0",
            "37.0, 127.0, 38.0, 127.0"
    })
    void findActivePartiesWithin_invalidBounds(double swLat, double swLng, double neLat, double neLng) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.findActivePartiesWithin(swLat, swLng, neLat, neLng));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.INVALID_MAP_BOUNDS);
        verify(parties, never()).findSummariesWithinBounds(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }

    // ───────────────────────── searchReservations ─────────────────────────

    @DisplayName("searchReservations - 도착지를 넣으면 출발지·도착지와 반경 1000m·상한 100 으로 조회해 그대로 돌려준다")
    @Test
    void searchReservations() {

        /*
         * settings
         */
        LocalDate date = todayKst().plusDays(1);
        List<NearbyReservation> found = List.of(new NearbyReservation(reservationSummary(), true));

        doReturn(found).when(parties).findReservationsWithinRadius(eq(kstMidnight(date)), eq(kstMidnight(date.plusDays(1))),
                eq(DEPARTURE), eq(DESTINATION), eq(1000), eq(100));

        /*
         * test
         */
        List<NearbyReservation> result = partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG, date);

        /*
         * verify
         */
        assertThat(result).isEqualTo(found);
    }

    @DisplayName("searchReservations - 도착지 없이 검색하면 도착지를 null 로 넘긴다")
    @Test
    void searchReservations_withoutDestination() {

        /*
         * settings
         */
        LocalDate date = todayKst().plusDays(1);
        List<NearbyReservation> found = List.of(new NearbyReservation(reservationSummary(), null));

        doReturn(found).when(parties).findReservationsWithinRadius(eq(kstMidnight(date)), eq(kstMidnight(date.plusDays(1))),
                eq(DEPARTURE), isNull(), eq(1000), eq(100));

        /*
         * test
         */
        List<NearbyReservation> result = partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, null, null, date);

        /*
         * verify
         */
        assertThat(result).isEqualTo(found);
    }

    @DisplayName("searchReservations - 오늘을 검색하면 00:00 이 아니라 지금부터 조회해 이미 출발한 방을 뺀다")
    @Test
    void searchReservations_todayStartsNow() {

        /*
         * settings
         */
        LocalDate today = todayKst();
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        Instant before = Instant.now();

        /*
         * test
         */
        partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, null, null, today);

        /*
         * verify
         */
        Instant after = Instant.now();
        verify(parties).findReservationsWithinRadius(from.capture(), eq(kstMidnight(today.plusDays(1))), eq(DEPARTURE), isNull(), eq(1000), eq(100));
        assertThat(from.getValue()).isBetween(before, after);
    }

    @DisplayName("searchReservations - 오늘이 아닌 날은 그날 00:00(KST)부터 다음 날 00:00(KST) 전까지 조회한다")
    @ParameterizedTest(name = "오늘 + {0}일")
    @ValueSource(ints = {1, 7})
    void searchReservations_otherDayStartsAtMidnight(int daysAhead) {

        /*
         * settings
         */
        LocalDate date = todayKst().plusDays(daysAhead);

        /*
         * test
         */
        partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, null, null, date);

        /*
         * verify
         */
        verify(parties).findReservationsWithinRadius(eq(kstMidnight(date)), eq(kstMidnight(date.plusDays(1))), eq(DEPARTURE), isNull(), eq(1000), eq(100));
    }

    @DisplayName("searchReservations - 오늘과 7일 뒤는 경계값이라 조회한다")
    @ParameterizedTest(name = "오늘 + {0}일")
    @ValueSource(ints = {0, 7})
    void searchReservations_dateEdges(int daysAhead) {

        /*
         * test
         */
        List<NearbyReservation> result = partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG, todayKst().plusDays(daysAhead));

        /*
         * verify
         */
        assertThat(result).isEmpty();
        verify(parties).findReservationsWithinRadius(any(), any(), eq(DEPARTURE), eq(DESTINATION), eq(1000), eq(100));
    }

    @DisplayName("searchReservations - 어제나 8일 뒤면 INVALID_SEARCH_DATE 이고 조회하지 않는다")
    @ParameterizedTest(name = "오늘 + {0}일")
    @ValueSource(ints = {-1, 8})
    void searchReservations_dateOutOfRange(int daysAhead) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG,
                todayKst().plusDays(daysAhead)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.INVALID_SEARCH_DATE);
        verifyNoInteractions(parties);
    }

    @DisplayName("searchReservations - 도착지 위도·경도 중 하나만 오면 INCOMPLETE_DESTINATION 이고 조회하지 않는다")
    @ParameterizedTest(name = "destLat={0}, destLng={1}")
    @CsvSource({
            "37.3948, ",
            ", 127.1112"
    })
    void searchReservations_incompleteDestination(Double destLat, Double destLng) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, destLat, destLng, todayKst().plusDays(1)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.INCOMPLETE_DESTINATION);
        verifyNoInteractions(parties);
    }

    @DisplayName("searchReservations - 출발지나 도착지가 서비스 지역 밖이면 OUT_OF_SERVICE_AREA 이고 조회하지 않는다")
    @ParameterizedTest(name = "{0}, {1} → {2}, {3}")
    @CsvSource({
            "0.0, 0.0, 37.3948, 127.1112",
            "37.4979, 127.0276, 35.6812, 139.7671",
            "32.99, 127.0276, , ",
            "37.4979, 132.01, , "
    })
    void searchReservations_outOfServiceArea(double depLat, double depLng, Double destLat, Double destLng) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.searchReservations(depLat, depLng, destLat, destLng, todayKst().plusDays(1)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.OUT_OF_SERVICE_AREA);
        verifyNoInteractions(parties);
    }

    // ───────────────────────── confirm ─────────────────────────

    @DisplayName("confirm - 만남 장소 없이 확정하면 잠금 조회한 방에 약속 시간만 담아 저장하고 커밋 후 confirmed 에 기존 출발지를 실어 보낸다")
    @Test
    void confirm() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 2L, HOST_ID, MEMBER_ID);
        Instant departureAt = Instant.now().plus(Duration.ofMinutes(30));

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.confirm(PARTY_ID, HOST_ID, new MeetingPlan(departureAt, DEPARTURE, "강남역"));

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getDepartureAt()).isEqualTo(departureAt);
        assertThat(party.getDepartureLocation()).isEqualTo(DEPARTURE);
        assertThat(party.getDeparture()).isEqualTo("강남역");
        assertThat(party.getVersion()).isEqualTo(3L);

        InOrder order = inOrder(parties, afterCommitExecutor);
        order.verify(parties).findByIdForUpdate(eq(PARTY_ID));
        order.verify(parties).save(eq(party));
        order.verify(afterCommitExecutor).execute(eq(SSE_TASK), any());
        verify(parties, never()).findById(any());
        verifyNoInteractions(eventPublisher, rideRecords);

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();

        verify(partyChangeNotifier).confirmed(eq(PARTY_ID), eq(new PartyConfirmed("COMPLETED", departureAt, DEPARTURE_LAT, DEPARTURE_LNG, "강남역", 3L)));
    }

    @DisplayName("confirm - 만남 장소를 주면 도메인에 그대로 전달돼 출발지가 바뀌고 confirmed 에 바뀐 departureLat·departureLng·departure 가 실린다")
    @Test
    void confirm_withMeetingPlace() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 2L, HOST_ID, MEMBER_ID);
        Instant departureAt = Instant.now().plus(Duration.ofMinutes(30));
        Location meetingPoint = new Location(37.4985, 127.0290);
        MeetingPlan plan = new MeetingPlan(departureAt, meetingPoint, "강남역 2번 출구");

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.confirm(PARTY_ID, HOST_ID, plan);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getDepartureAt()).isEqualTo(departureAt);
        assertThat(party.getDepartureLocation()).isEqualTo(meetingPoint);
        assertThat(party.getDeparture()).isEqualTo("강남역 2번 출구");
        assertThat(party.getDestinationLocation()).isEqualTo(DESTINATION);
        assertThat(party.getVersion()).isEqualTo(3L);
        verify(parties).save(eq(party));

        capturedAfterCommitTask().run();
        verify(partyChangeNotifier).confirmed(eq(PARTY_ID), eq(new PartyConfirmed("COMPLETED", departureAt, 37.4985, 127.0290, "강남역 2번 출구", 3L)));
    }

    @DisplayName("confirm - 만남 장소가 도착지와 같으면 SAME_DEPARTURE_DESTINATION 이고 출발지·상태가 그대로이며 저장·신호가 없다")
    @Test
    void confirm_meetingPlaceSameAsDestination() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, 2L, HOST_ID, MEMBER_ID);
        MeetingPlan plan = new MeetingPlan(Instant.now().plus(Duration.ofMinutes(30)), DESTINATION, "판교역");

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.confirm(PARTY_ID, HOST_ID, plan));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.SAME_DEPARTURE_DESTINATION);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.getDepartureAt()).isNull();
        assertThat(party.getDepartureLocation()).isEqualTo(DEPARTURE);
        assertThat(party.getDeparture()).isEqualTo("강남역");
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("confirm - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void confirm_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.confirm(PARTY_ID, HOST_ID, new MeetingPlan(Instant.now().plus(Duration.ofMinutes(30)), DEPARTURE, "강남역")));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("confirm - 도메인이 거절하면 그 오류가 나가고 저장·신호가 없다")
    @ParameterizedTest(name = "{0} - {3}")
    @MethodSource("confirmRejections")
    void confirm_rejected(MatchingErrorCode expected, Party party, Long memberId, String reason, Duration fromNow) {

        /*
         * settings
         */
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.confirm(PARTY_ID, memberId, new MeetingPlan(Instant.now().plus(fromNow), DEPARTURE, "강남역")));

        /*
         * verify
         */
        assertBusinessException(thrown, expected);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    static Stream<Arguments> confirmRejections() {
        Duration ok = Duration.ofMinutes(30);
        return Stream.of(
                Arguments.of(MatchingErrorCode.NOT_PARTY_MEMBER, party(PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID), THIRD_ID, "방에 없는 사람", ok),
                Arguments.of(MatchingErrorCode.NOT_PARTY_HOST, party(PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID), MEMBER_ID, "방장이 아닌 멤버", ok),
                Arguments.of(MatchingErrorCode.NOT_REALTIME_PARTY, party(RoomType.RESERVED, Instant.now().plus(Duration.ofDays(1)), PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID), HOST_ID, "예약 방", ok),
                Arguments.of(MatchingErrorCode.ALREADY_CONFIRMED, party(RoomType.REALTIME, Instant.now().plus(Duration.ofMinutes(20)), PartyStatus.COMPLETED, 3, 0L, HOST_ID, MEMBER_ID), HOST_ID, "이미 확정한 방", ok),
                Arguments.of(MatchingErrorCode.NOT_ENOUGH_MEMBERS, party(PartyStatus.ACTIVE, 3, 0L, HOST_ID), HOST_ID, "방장 혼자", ok),
                Arguments.of(MatchingErrorCode.PARTY_CLOSED, party(PartyStatus.CANCELED, 3, 0L, HOST_ID, MEMBER_ID), HOST_ID, "취소된 방", ok),
                Arguments.of(MatchingErrorCode.PARTY_CLOSED, party(PartyStatus.FINISHED, 2, 0L, HOST_ID, MEMBER_ID), HOST_ID, "끝난 방", ok),
                Arguments.of(MatchingErrorCode.INVALID_MEETING_TIME, party(PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID), HOST_ID, "지금부터 5분이 안 되는 시각", Duration.ofMinutes(4)),
                Arguments.of(MatchingErrorCode.INVALID_MEETING_TIME, party(PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID), HOST_ID, "지난 시각", Duration.ofMinutes(-2)),
                Arguments.of(MatchingErrorCode.INVALID_MEETING_TIME, party(PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID), HOST_ID, "1시간 넘게 뒤", Duration.ofMinutes(61)));
    }

    // ───────────────────────── arrive ─────────────────────────

    @DisplayName("arrive - 잠금 조회한 실시간 방의 출발지 100m 안인지 요청 좌표로 DB 에 묻고, 안이면 도착을 남기고 저장한 뒤 커밋 후 member-arrived 에 publicId 와 오른 version 을 싣는다")
    @Test
    void arrive() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, 4L,
                waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1));

        doReturn(Map.of(MEMBER_ID, summary(MEMBER_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(true).when(parties).isNearDeparture(eq(PARTY_ID), eq(NEAR_LAT), eq(DEPARTURE_LNG), eq(ARRIVAL_RADIUS));

        /*
         * test
         */
        partyService.arrive(PARTY_ID, MEMBER_ID, NEAR_LAT, DEPARTURE_LNG);

        /*
         * verify
         */
        assertThat(memberOf(party, MEMBER_ID).isArrived()).isTrue();
        assertThat(memberOf(party, HOST_ID).isArrived()).isFalse();
        assertThat(party.getVersion()).isEqualTo(5L);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);

        InOrder order = inOrder(userAccess, parties, afterCommitExecutor);
        order.verify(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        order.verify(parties).findByIdForUpdate(eq(PARTY_ID));
        order.verify(parties).isNearDeparture(eq(PARTY_ID), eq(NEAR_LAT), eq(DEPARTURE_LNG), eq(ARRIVAL_RADIUS));
        order.verify(parties).save(eq(party));
        order.verify(afterCommitExecutor).execute(eq(SSE_TASK), any());
        verify(parties, never()).findById(any());
        verifyNoInteractions(eventPublisher, rideRecords);

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();

        verify(partyChangeNotifier).memberArrived(eq(PARTY_ID), eq(new MemberArrived(MEMBER_PUBLIC_ID, 5L)));
    }

    @DisplayName("arrive - 예약 방은 출발지 400m 안인지 DB 에 묻는다 - 출발지가 장소 검색 결과의 중심점이라 실제 만나는 출구까지 넓게 받는다")
    @Test
    void arrive_reservationRadius() {

        /*
         * settings
         */
        Party party = party(RoomType.RESERVED, Instant.now().plus(Duration.ofMinutes(5)), PartyStatus.ACTIVE, 3, 4L, HOST_ID, MEMBER_ID);

        doReturn(Map.of(MEMBER_ID, summary(MEMBER_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(true).when(parties).isNearDeparture(eq(PARTY_ID), eq(FAR_LAT), eq(DEPARTURE_LNG), eq(RESERVATION_ARRIVAL_RADIUS));

        /*
         * test
         */
        partyService.arrive(PARTY_ID, MEMBER_ID, FAR_LAT, DEPARTURE_LNG);

        /*
         * verify
         */
        verify(parties).isNearDeparture(eq(PARTY_ID), eq(FAR_LAT), eq(DEPARTURE_LNG), eq(RESERVATION_ARRIVAL_RADIUS));
        assertThat(memberOf(party, MEMBER_ID).isArrived()).isTrue();
        verify(parties).save(eq(party));
    }

    @DisplayName("arrive - 이미 도착한 멤버가 다시 누르면 오류 없이 저장·신호를 하지 않는다")
    @Test
    void arrive_alreadyArrived() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, 4L,
                waitingMember(HOST_ID, 0), arrivedMember(MEMBER_ID, 1));
        Instant firstArrivedAt = memberOf(party, MEMBER_ID).getArrivedAt();

        doReturn(Map.of(MEMBER_ID, summary(MEMBER_PUBLIC_ID))).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(false).when(parties).isNearDeparture(eq(PARTY_ID), eq(FAR_LAT), eq(DEPARTURE_LNG), eq(ARRIVAL_RADIUS));

        /*
         * test
         */
        // DB 가 출발지 밖이라고 답해도 이미 도착했으니 도메인이 다시 보지 않는다
        partyService.arrive(PARTY_ID, MEMBER_ID, FAR_LAT, DEPARTURE_LNG);

        /*
         * verify
         */
        assertThat(memberOf(party, MEMBER_ID).getArrivedAt()).isEqualTo(firstArrivedAt);
        assertThat(party.getVersion()).isEqualTo(4L);
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, partyChangeNotifier, eventPublisher);
    }

    @DisplayName("arrive - 유저 요약이 없어도 도착되고 신호의 publicId 는 null 이다")
    @Test
    void arrive_noMemberSummary() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, 0L,
                waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1));

        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(HOST_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(true).when(parties).isNearDeparture(eq(PARTY_ID), eq(DEPARTURE_LAT), eq(DEPARTURE_LNG), eq(ARRIVAL_RADIUS));

        /*
         * test
         */
        partyService.arrive(PARTY_ID, HOST_ID, DEPARTURE_LAT, DEPARTURE_LNG);

        /*
         * verify
         */
        assertThat(memberOf(party, HOST_ID).isArrived()).isTrue();
        capturedAfterCommitTask().run();
        verify(partyChangeNotifier).memberArrived(eq(PARTY_ID), eq(new MemberArrived(null, 1L)));
    }

    @DisplayName("arrive - 서비스 지역 밖 좌표면 OUT_OF_SERVICE_AREA 이고 아무것도 조회하지 않는다")
    @Test
    void arrive_outOfServiceArea() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.arrive(PARTY_ID, MEMBER_ID, 35.6812, 139.7671));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.OUT_OF_SERVICE_AREA);
        verifyNoInteractions(parties, userAccess, afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("arrive - 없는 방이면 PARTY_NOT_FOUND 이고 출발지 거리를 묻지 않는다")
    @Test
    void arrive_partyNotFound() {

        /*
         * settings
         */
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.arrive(PARTY_ID, MEMBER_ID, NEAR_LAT, DEPARTURE_LNG));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verify(parties, never()).isNearDeparture(any(), anyDouble(), anyDouble(), anyDouble());
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("arrive - 도메인이 거절하면 그 오류가 나가고 저장·신호가 없다")
    @ParameterizedTest(name = "{0} - {5}")
    @MethodSource("arriveRejections")
    void arrive_rejected(MatchingErrorCode expected, Party party, Long memberId, double latitude, boolean nearDeparture, String reason) {

        /*
         * settings
         */
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(memberId)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(nearDeparture).when(parties).isNearDeparture(eq(PARTY_ID), eq(latitude), eq(DEPARTURE_LNG), eq(ARRIVAL_RADIUS));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.arrive(PARTY_ID, memberId, latitude, DEPARTURE_LNG));

        /*
         * verify
         */
        assertBusinessException(thrown, expected);
        assertThat(party.getMembers()).noneMatch(PartyMember::isArrived);
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, partyChangeNotifier, eventPublisher);
    }

    static Stream<Arguments> arriveRejections() {
        Instant started = Instant.now().minus(Duration.ofMinutes(5));
        return Stream.of(
                Arguments.of(MatchingErrorCode.NOT_PARTY_MEMBER, confirmedParty(started, PartyStatus.COMPLETED, 0L, waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), THIRD_ID, NEAR_LAT, true, "방에 없는 사람"),
                Arguments.of(MatchingErrorCode.PARTY_NOT_CONFIRMED, party(PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID), HOST_ID, NEAR_LAT, true, "약속 확정 전 실시간 방"),
                Arguments.of(MatchingErrorCode.PARTY_CLOSED, confirmedParty(started, PartyStatus.FINISHED, 0L, waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, NEAR_LAT, true, "이미 끝난 방"),
                Arguments.of(MatchingErrorCode.ARRIVAL_NOT_OPEN, confirmedParty(Instant.now().plus(Duration.ofMinutes(30)), PartyStatus.COMPLETED, 0L, waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, NEAR_LAT, true, "약속 시간 10분 전보다 이름"),
                Arguments.of(MatchingErrorCode.TOO_FAR_FROM_DEPARTURE, confirmedParty(started, PartyStatus.COMPLETED, 0L, waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, FAR_LAT, false, "출발지에서 약 111m - DB 가 반경 밖이라고 답함"));
    }

    // ───────────────────────── depart ─────────────────────────

    @DisplayName("depart - 도착한 방장이 누르면 FINISHED 로 저장하고 하차 기록·닫힘 이벤트를 남긴 뒤 커밋 후 closed 를 보낸다. 도착하지 않은 멤버는 탑승자에서 빠질 뿐이다")
    @Test
    void depart() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, 6L,
                arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1), arrivedMember(THIRD_ID, 2));

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.depart(PARTY_ID, HOST_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getVersion()).isEqualTo(7L);
        assertThat(party.getMembers()).hasSize(3);
        assertThat(party.riderIds()).containsExactly(HOST_ID, THIRD_ID);

        InOrder order = inOrder(parties, rideRecords, eventPublisher, afterCommitExecutor);
        order.verify(parties).findByIdForUpdate(eq(PARTY_ID));
        order.verify(parties).save(eq(party));
        order.verify(rideRecords).save(any(RideRecord.class));
        order.verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, "FINISHED")));
        order.verify(afterCommitExecutor).execute(eq(SSE_TASK), any());
        verifyNoInteractions(userAccess);

        ArgumentCaptor<RideRecord> captor = ArgumentCaptor.forClass(RideRecord.class);
        verify(rideRecords).save(captor.capture());
        RideRecord record = captor.getValue();
        assertThat(record.getMatchId()).isEqualTo(PARTY_ID);
        assertThat(record.getDeparturePlace()).isEqualTo("강남역");
        assertThat(record.getDestinationPlace()).isEqualTo("판교역");
        assertThat(record.isDroppedOff()).isTrue();

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("FINISHED")));
        verify(partyChangeNotifier, never()).memberArrived(any(), any());
    }

    @DisplayName("depart - 모두 도착했으면 전원이 탑승자다")
    @Test
    void depart_everyoneArrived() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, 0L,
                arrivedMember(HOST_ID, 0), arrivedMember(MEMBER_ID, 1));

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.depart(PARTY_ID, HOST_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.riderIds()).containsExactly(HOST_ID, MEMBER_ID);
        verify(parties).save(eq(party));
        verify(rideRecords).save(any(RideRecord.class));
    }

    @DisplayName("depart - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void depart_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.depart(PARTY_ID, HOST_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verify(parties, never()).save(any());
        verifyNoInteractions(rideRecords, eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("depart - 도메인이 거절하면 그 오류가 나가고 저장·기록·이벤트·신호가 없다")
    @ParameterizedTest(name = "{0} - {3}")
    @MethodSource("departRejections")
    void depart_rejected(MatchingErrorCode expected, Party party, Long memberId, String reason) {

        /*
         * settings
         */
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.depart(PARTY_ID, memberId));

        /*
         * verify
         */
        assertBusinessException(thrown, expected);
        verify(parties, never()).save(any());
        verifyNoInteractions(rideRecords, eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    static Stream<Arguments> departRejections() {
        Instant started = Instant.now().minus(Duration.ofMinutes(5));
        return Stream.of(
                Arguments.of(MatchingErrorCode.NOT_PARTY_MEMBER, confirmedParty(started, PartyStatus.COMPLETED, 0L, arrivedMember(HOST_ID, 0), arrivedMember(MEMBER_ID, 1)), THIRD_ID, "방에 없는 사람"),
                Arguments.of(MatchingErrorCode.NOT_PARTY_HOST, confirmedParty(started, PartyStatus.COMPLETED, 0L, arrivedMember(HOST_ID, 0), arrivedMember(MEMBER_ID, 1)), MEMBER_ID, "방장이 아닌 멤버 - 도착했어도 못 누른다"),
                Arguments.of(MatchingErrorCode.PARTY_NOT_CONFIRMED, party(PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID), HOST_ID, "약속 확정 전 실시간 방"),
                Arguments.of(MatchingErrorCode.PARTY_CLOSED, confirmedParty(started, PartyStatus.FINISHED, 0L, arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, "이미 끝난 방"),
                Arguments.of(MatchingErrorCode.PARTY_CLOSED, confirmedParty(started, PartyStatus.CANCELED, 0L, arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, "취소된 방"),
                Arguments.of(MatchingErrorCode.NOT_ENOUGH_MEMBERS, party(RoomType.RESERVED, started, PartyStatus.ACTIVE, 3, 0L, HOST_ID), HOST_ID, "혼자인 예약 방"),
                Arguments.of(MatchingErrorCode.MEETING_NOT_STARTED, confirmedParty(Instant.now().plus(Duration.ofMinutes(10)), PartyStatus.COMPLETED, 0L, arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, "약속 시간 전"),
                Arguments.of(MatchingErrorCode.NOT_ARRIVED, confirmedParty(started, PartyStatus.COMPLETED, 0L, waitingMember(HOST_ID, 0), arrivedMember(MEMBER_ID, 1)), HOST_ID, "방장이 도착 전"));
    }

    // ───────────────────────── autoClose ─────────────────────────

    @DisplayName("autoClose - 도착한 멤버가 있는 확정 방은 FINISHED 로 저장하고 하차 기록·닫힘 이벤트를 남긴 뒤 커밋 후 closed 를 보낸다. 탑승자는 도착한 멤버뿐이다")
    @Test
    void autoClose_someArrived() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofDays(1)), PartyStatus.COMPLETED, 6L,
                arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1), arrivedMember(THIRD_ID, 2));

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getVersion()).isEqualTo(7L);
        assertThat(party.riderIds()).containsExactly(HOST_ID, THIRD_ID);

        InOrder order = inOrder(parties, rideRecords, eventPublisher, afterCommitExecutor);
        order.verify(parties).findByIdForUpdate(eq(PARTY_ID));
        order.verify(parties).save(eq(party));
        order.verify(rideRecords).save(any(RideRecord.class));
        order.verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, "FINISHED")));
        order.verify(afterCommitExecutor).execute(eq(SSE_TASK), any());
        verifyNoInteractions(userAccess);

        ArgumentCaptor<RideRecord> captor = ArgumentCaptor.forClass(RideRecord.class);
        verify(rideRecords).save(captor.capture());
        RideRecord record = captor.getValue();
        assertThat(record.getMatchId()).isEqualTo(PARTY_ID);
        assertThat(record.getDeparturePlace()).isEqualTo("강남역");
        assertThat(record.getDestinationPlace()).isEqualTo("판교역");
        assertThat(record.isDroppedOff()).isTrue();

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("FINISHED")));
    }

    @DisplayName("autoClose - 아무도 도착하지 않은 확정 방은 CANCELED 이고 하차 기록 없이 닫힘 이벤트와 커밋 후 closed 를 보낸다")
    @Test
    void autoClose_nobodyArrived() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofDays(1)), PartyStatus.COMPLETED, 6L,
                waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1));

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.getVersion()).isEqualTo(7L);
        assertThat(party.riderIds()).isEmpty();
        verify(parties).save(eq(party));
        verifyNoInteractions(rideRecords);
        verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, "CANCELED")));

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("CANCELED")));
    }

    @DisplayName("autoClose - 약속이 없거나 혼자인 방은 동승이 없었으니 CANCELED 로 닫고 하차 기록을 남기지 않는다")
    @ParameterizedTest(name = "{1}")
    @MethodSource("autoCloseCancellations")
    void autoClose_unconfirmedOrAlone(Party party, String reason) {

        /*
         * settings
         */
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.getVersion()).isEqualTo(6L);
        verify(parties).save(eq(party));
        verifyNoInteractions(rideRecords);
        verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, "CANCELED")));

        Runnable task = capturedAfterCommitTask();
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("CANCELED")));
    }

    static Stream<Arguments> autoCloseCancellations() {
        return Stream.of(
                Arguments.of(party(PartyStatus.ACTIVE, 3, 5L, HOST_ID, MEMBER_ID), "약속을 정하지 않은 채 만든 지 1일이 지난 실시간 방"),
                Arguments.of(party(RoomType.RESERVED, Instant.now().minus(Duration.ofDays(1)), PartyStatus.ACTIVE, 3, 5L, HOST_ID), "혼자인 예약 방"));
    }

    @DisplayName("autoClose - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void autoClose_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.autoClose(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verify(parties, never()).save(any());
        verifyNoInteractions(rideRecords, eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("autoClose - 조회와 잠금 사이에 이미 닫힌 방이면 예외 없이 아무것도 바꾸지 않고 저장·탑승 기록·이벤트·신호가 없다")
    @ParameterizedTest(name = "{1}")
    @MethodSource("closedParties")
    void autoClose_alreadyClosed(Party party, String reason) {

        /*
         * settings
         */
        PartyStatus before = party.getStatus();

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(before);
        assertThat(party.getVersion()).isEqualTo(6L);
        verify(parties, never()).save(any());
        verifyNoInteractions(rideRecords, eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    static Stream<Arguments> closedParties() {
        Instant started = Instant.now().minus(Duration.ofDays(1));
        return Stream.of(
                Arguments.of(confirmedParty(started, PartyStatus.FINISHED, 6L, arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), "출발로 이미 끝난 방"),
                Arguments.of(party(PartyStatus.CANCELED, 2, 6L, HOST_ID), "이미 취소된 방"));
    }

    // ───────────────────────── realtimeMatch ─────────────────────────

    @DisplayName("realtimeMatch - 반경 300m 후보를 도보 시간 합 오름차순으로 돌려준다")
    @Test
    void realtimeMatch() {

        /*
         * settings
         */
        NearbyPartySummary far = nearby(10L, 37.50, 127.03, 37.40, 127.12);
        NearbyPartySummary near = nearby(20L, 37.51, 127.04, 37.41, 127.13);
        Coordinate departure = new Coordinate(DEPARTURE_LAT, DEPARTURE_LNG);
        Coordinate destination = new Coordinate(DESTINATION_LAT, DESTINATION_LNG);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(List.of(far, near)).when(parties).findMatchesWithinRadius(eq(DEPARTURE_LAT), eq(DEPARTURE_LNG), eq(DESTINATION_LAT), eq(DESTINATION_LNG), eq(300));
        doReturn(List.of(new WalkingEstimate(300), new WalkingEstimate(100)))
                .when(walkRouteFinder).fromTo(eq(departure), eq(List.of(new Coordinate(37.50, 127.03), new Coordinate(37.51, 127.04))));
        doReturn(List.of(new WalkingEstimate(200), new WalkingEstimate(50)))
                .when(walkRouteFinder).toFrom(eq(destination), eq(List.of(new Coordinate(37.40, 127.12), new Coordinate(37.41, 127.13))));

        /*
         * test
         */
        List<MatchCandidate> result = partyService.realtimeMatch(MEMBER_ID, realtimeCommand());

        /*
         * verify
         */
        assertThat(result).containsExactly(new MatchCandidate(near, 150), new MatchCandidate(far, 500));
    }

    @DisplayName("realtimeMatch - 도보 시간 합이 같으면 조회된 순서를 유지한다")
    @Test
    void realtimeMatch_sameTotal() {

        /*
         * settings
         */
        NearbyPartySummary first = nearby(10L, 37.50, 127.03, 37.40, 127.12);
        NearbyPartySummary second = nearby(20L, 37.51, 127.04, 37.41, 127.13);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(List.of(first, second)).when(parties).findMatchesWithinRadius(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(300));
        doReturn(List.of(new WalkingEstimate(100), new WalkingEstimate(200))).when(walkRouteFinder).fromTo(any(), any());
        doReturn(List.of(new WalkingEstimate(200), new WalkingEstimate(100))).when(walkRouteFinder).toFrom(any(), any());

        /*
         * test
         */
        List<MatchCandidate> result = partyService.realtimeMatch(MEMBER_ID, realtimeCommand());

        /*
         * verify
         */
        assertThat(result).containsExactly(new MatchCandidate(first, 300), new MatchCandidate(second, 300));
    }

    @DisplayName("realtimeMatch - 도보 경로가 없는 후보(탑승 쪽·하차 쪽 null)는 뺀다")
    @Test
    void realtimeMatch_noWalkingRoute() {

        /*
         * settings
         */
        NearbyPartySummary noPickup = nearby(10L, 37.50, 127.03, 37.40, 127.12);
        NearbyPartySummary noDropOff = nearby(20L, 37.51, 127.04, 37.41, 127.13);
        NearbyPartySummary reachable = nearby(30L, 37.52, 127.05, 37.42, 127.14);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(List.of(noPickup, noDropOff, reachable)).when(parties).findMatchesWithinRadius(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(300));
        doReturn(Arrays.asList(null, new WalkingEstimate(100), new WalkingEstimate(10))).when(walkRouteFinder).fromTo(any(), any());
        doReturn(Arrays.asList(new WalkingEstimate(100), null, new WalkingEstimate(20))).when(walkRouteFinder).toFrom(any(), any());

        /*
         * test
         */
        List<MatchCandidate> result = partyService.realtimeMatch(MEMBER_ID, realtimeCommand());

        /*
         * verify
         */
        assertThat(result).containsExactly(new MatchCandidate(reachable, 30));
    }

    @DisplayName("realtimeMatch - 후보가 없으면 빈 목록")
    @Test
    void realtimeMatch_noCandidates() {

        /*
         * settings
         */
        Coordinate departure = new Coordinate(DEPARTURE_LAT, DEPARTURE_LNG);
        Coordinate destination = new Coordinate(DESTINATION_LAT, DESTINATION_LNG);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(List.of()).when(parties).findMatchesWithinRadius(eq(DEPARTURE_LAT), eq(DEPARTURE_LNG), eq(DESTINATION_LAT), eq(DESTINATION_LNG), eq(300));
        doReturn(List.of()).when(walkRouteFinder).fromTo(eq(departure), eq(List.of()));
        doReturn(List.of()).when(walkRouteFinder).toFrom(eq(destination), eq(List.of()));

        /*
         * test
         */
        List<MatchCandidate> result = partyService.realtimeMatch(MEMBER_ID, realtimeCommand());

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    @DisplayName("realtimeMatch - 진행 중인 방이 있으면 ALREADY_JOINED_OTHER_PARTY 이고 후보·도보 조회를 하지 않는다")
    @Test
    void realtimeMatch_alreadyInOngoingParty() {

        /*
         * settings
         */
        doReturn(true).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.realtimeMatch(MEMBER_ID, realtimeCommand()));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
        verify(parties, never()).findMatchesWithinRadius(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt());
        verifyNoInteractions(walkRouteFinder);
    }

    @DisplayName("realtimeMatch - 좌표가 서비스 지역 밖이면 OUT_OF_SERVICE_AREA 이고 아무것도 조회하지 않는다")
    @ParameterizedTest(name = "{0}, {1} → {2}, {3}")
    @CsvSource({
            "0.0, 0.0, 37.0, 127.0",
            "37.0, 127.0, 0.0, 0.0",
            "32.99, 127.0, 37.0, 127.0",
            "37.0, 127.0, 37.0, 132.01"
    })
    void realtimeMatch_outOfServiceArea(double departureLat, double departureLng, double destLat, double destLng) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.realtimeMatch(MEMBER_ID, new RealTimeMatchingCommand(departureLat, departureLng, destLat, destLng)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.OUT_OF_SERVICE_AREA);
        verify(parties, never()).existsOngoingRealtimeByMemberId(anyLong());
        verifyNoInteractions(walkRouteFinder);
    }

    @DisplayName("realtimeMatch - 서비스 지역 경계 좌표는 허용한다")
    @ParameterizedTest(name = "{0}, {1} → {2}, {3}")
    @CsvSource({
            "33.0, 124.0, 39.0, 132.0",
            "39.0, 132.0, 33.0, 124.0"
    })
    void realtimeMatch_boundaryCoordinate(double departureLat, double departureLng, double destLat, double destLng) {

        /*
         * settings
         */
        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(List.of()).when(parties).findMatchesWithinRadius(eq(departureLat), eq(departureLng), eq(destLat), eq(destLng), eq(300));
        doReturn(List.of()).when(walkRouteFinder).fromTo(eq(new Coordinate(departureLat, departureLng)), eq(List.of()));
        doReturn(List.of()).when(walkRouteFinder).toFrom(eq(new Coordinate(destLat, destLng)), eq(List.of()));

        /*
         * test
         */
        List<MatchCandidate> result = partyService.realtimeMatch(MEMBER_ID, new RealTimeMatchingCommand(departureLat, departureLng, destLat, destLng));

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    @DisplayName("realtimeMatch - 탑승지 도보 조회가 실패하면 ROUTE_SEARCH_FAILED 이고 하차지 조회를 하지 않는다")
    @Test
    void realtimeMatch_pickupRouteFailed() {

        /*
         * settings
         */
        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(List.of(nearby(10L, 37.50, 127.03, 37.40, 127.12))).when(parties).findMatchesWithinRadius(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(300));
        doThrow(new BusinessException(MatchingErrorCode.ROUTE_SEARCH_FAILED)).when(walkRouteFinder).fromTo(any(), any());

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.realtimeMatch(MEMBER_ID, realtimeCommand()));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ROUTE_SEARCH_FAILED);
        verify(walkRouteFinder, never()).toFrom(any(), any());
    }

    @DisplayName("realtimeMatch - 하차지 도보 조회가 실패하면 ROUTE_SEARCH_FAILED")
    @Test
    void realtimeMatch_dropOffRouteFailed() {

        /*
         * settings
         */
        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(MEMBER_ID));
        doReturn(List.of(nearby(10L, 37.50, 127.03, 37.40, 127.12))).when(parties).findMatchesWithinRadius(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(300));
        doReturn(List.of(new WalkingEstimate(100))).when(walkRouteFinder).fromTo(any(), any());
        doThrow(new BusinessException(MatchingErrorCode.ROUTE_SEARCH_FAILED)).when(walkRouteFinder).toFrom(any(), any());

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.realtimeMatch(MEMBER_ID, realtimeCommand()));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ROUTE_SEARCH_FAILED);
    }

    // ───────────────────────── 공통 도우미 ─────────────────────────

    private static OpenPartyCommand createCommand(int capacity) {
        return new OpenPartyCommand(HOST_ID, DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG, "강남역", "판교역", capacity);
    }

    private static OpenReservationCommand reservationCommand() {
        return new OpenReservationCommand(HOST_ID, DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG, "강남역", "판교역", 3,
                Instant.now().plus(Duration.ofDays(1)));
    }

    private static RealTimeMatchingCommand realtimeCommand() {
        return new RealTimeMatchingCommand(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG);
    }

    private static NearbyPartySummary nearby(Long id, double departureLat, double departureLng, double destinationLat, double destinationLng) {
        return new NearbyPartySummary(id, departureLat, departureLng, destinationLat, destinationLng, "출발", "도착", 3, 1);
    }

    private static MemberSummary summary(UUID publicId) {
        return new MemberSummary(publicId, "닉네임", "image.png");
    }

    private static ReservationSummary reservationSummary() {
        return new ReservationSummary(PARTY_ID, "강남역", "판교역", 1L, 3, PartyStatus.ACTIVE, DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG,
                BASE_TIME);
    }

    private static LocalDate todayKst() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    /** 그날 00:00(KST). 한국은 서머타임이 없어 늘 UTC+9 다 */
    private static Instant kstMidnight(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.ofHours(9));
    }

    /** 약속을 정하지 않은 실시간 방. memberIds 앞쪽일수록 먼저 합류한 멤버다 - 첫 번째가 방장 */
    private static Party party(PartyStatus status, int capacity, long version, Long... memberIds) {
        return party(RoomType.REALTIME, null, status, capacity, version, memberIds);
    }

    /** 방 종류와 약속(출발) 시각까지 정해 복원한 방. memberIds 앞쪽일수록 먼저 합류했고 첫 번째가 방장 */
    private static Party party(RoomType roomType, Instant departureAt, PartyStatus status, int capacity, long version, Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for (int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], BASE_TIME.plusSeconds(i), null));
        }

        boolean realtime = roomType == RoomType.REALTIME;

        return Party.load(PARTY_ID, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(capacity), members,
                memberIds.length == 0 ? null : memberIds[0], BASE_TIME, status, realtime ? 12000 : null, realtime ? 25 : null, realtime ? "encoded-path" : null,
                version, roomType, departureAt);
    }

    /** 방장이 departureAt 으로 약속을 확정한 실시간 방. members 앞쪽이 먼저 합류했고 첫 번째가 방장 */
    private static Party confirmedParty(Instant departureAt, PartyStatus status, long version, PartyMember... members) {
        return Party.load(PARTY_ID, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(3),
                new ArrayList<>(List.of(members)), members[0].getMemberId(), BASE_TIME, status, 12000, 25, "encoded-path",
                version, RoomType.REALTIME, departureAt);
    }

    /** 아직 도착하지 않은 멤버. order 가 작을수록 먼저 합류했다 */
    private static PartyMember waitingMember(Long memberId, int order) {
        return PartyMember.load(memberId, BASE_TIME.plusSeconds(order), null);
    }

    /** 출발지에 도착한 멤버 */
    private static PartyMember arrivedMember(Long memberId, int order) {
        return PartyMember.load(memberId, BASE_TIME.plusSeconds(order), BASE_TIME.plusSeconds(3600));
    }

    private static PartyMember memberOf(Party party, Long memberId) {
        return party.getMembers().stream()
                .filter(m -> m.getMemberId().equals(memberId))
                .findFirst()
                .orElseThrow();
    }

    /** 신호는 커밋 후 실행기에 맡긴 작업 안에서만 나가야 한다 */
    private Runnable capturedAfterCommitTask() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommitExecutor).execute(eq(SSE_TASK), captor.capture());

        return captor.getValue();
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
