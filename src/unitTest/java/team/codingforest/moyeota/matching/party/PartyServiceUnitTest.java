package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.driver.api.DriverSummary;
import team.codingforest.moyeota.matching.api.dto.PartyClosedEvent;
import team.codingforest.moyeota.matching.api.dto.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.api.dto.PartyMemberLeftEvent;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.completion.PartyCompletionPolicy;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyChangeNotifier;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.matching.party.domain.RoomType;
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
import team.codingforest.moyeota.user.api.NoShowPenalty;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Duration;
import java.time.Instant;
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
import static org.mockito.Mockito.doAnswer;
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
    private static final Long DRIVER_ID = 9L;
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
    // 도착을 받는 출발지 반경(Party.ARRIVAL_RADIUS_METERS) - 값이 바뀌면 이 테스트가 알아채도록 숫자로 적는다
    private static final double ARRIVAL_RADIUS = 100.0;
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
    private DriverAccess driverAccess;
    @Mock
    private UserAccess userAccess;
    @Mock
    private PartyCompletionPolicy partyCompletionPolicy;
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
    @Mock
    private NoShowPenalty noShowPenalty;
    @Mock
    private MeetingNotificationService meetingNotificationService;

    @InjectMocks
    private PartyService partyService;

    // ───────────────────────── open ─────────────────────────

    @DisplayName("open - 진행 중인 방을 확인하고 경로를 조회한 뒤 그 결과로 PartyOpener 에 저장을 맡긴다")
    @Test
    void open() {

        /*
         * settings
         */
        OpenPartyCommand command = openCommand(3);
        PartyResult opened = PartyResult.from(party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID));

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(HOST_ID));
        doReturn(ESTIMATE).when(routeService).estimate(eq(DEPARTURE_LAT), eq(DEPARTURE_LNG), eq(DESTINATION_LAT), eq(DESTINATION_LNG));
        doReturn(opened).when(partyOpener).open(eq(command), eq(ESTIMATE));

        /*
         * test
         */
        PartyResult result = partyService.open(command);

        /*
         * verify
         */
        assertThat(result).isSameAs(opened);
        InOrder order = inOrder(parties, routeService, partyOpener);
        order.verify(parties).existsOngoingRealtimeByMemberId(eq(HOST_ID));
        order.verify(routeService).estimate(eq(DEPARTURE_LAT), eq(DEPARTURE_LNG), eq(DESTINATION_LAT), eq(DESTINATION_LNG));
        order.verify(partyOpener).open(eq(command), eq(ESTIMATE));
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, partyCompletionPolicy, afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("open - 진행 중인 방이 있으면 ALREADY_JOINED_OTHER_PARTY 이고 경로 조회도 하지 않는다")
    @Test
    void open_alreadyInOngoingParty() {

        /*
         * settings
         */
        doReturn(true).when(parties).existsOngoingRealtimeByMemberId(eq(HOST_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.open(openCommand(3)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
        verifyNoInteractions(routeService, partyOpener);
    }

    @DisplayName("open - 경로 조회가 실패하면 예외가 그대로 나가고 저장을 맡기지 않는다")
    @Test
    void open_routeSearchFailed() {

        /*
         * settings
         */
        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(HOST_ID));
        doThrow(new BusinessException(MatchingErrorCode.ROUTE_SEARCH_FAILED))
                .when(routeService).estimate(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.open(openCommand(3)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ROUTE_SEARCH_FAILED);
        verifyNoInteractions(partyOpener);
    }

    @DisplayName("open - 저장 단계의 예외는 그대로 나간다")
    @Test
    void open_openerFailed() {

        /*
         * settings
         */
        OpenPartyCommand command = openCommand(3);

        doReturn(false).when(parties).existsOngoingRealtimeByMemberId(eq(HOST_ID));
        doReturn(ESTIMATE).when(routeService).estimate(eq(DEPARTURE_LAT), eq(DEPARTURE_LNG), eq(DESTINATION_LAT), eq(DESTINATION_LNG));
        doThrow(new BusinessException(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY)).when(partyOpener).open(eq(command), eq(ESTIMATE));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.open(command));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
    }

    /** 네이버 호출 동안 DB 커넥션을 쥐고 있으면 안 된다 - 트랜잭션은 PartyOpener 에만 있어야 한다 */
    @DisplayName("open - 경로 조회가 있는 바깥 메서드에는 트랜잭션이 없다")
    @Test
    void open_notTransactional() throws Exception {

        /*
         * test
         */
        boolean transactional = PartyService.class.getMethod("open", OpenPartyCommand.class)
                .isAnnotationPresent(Transactional.class);

        /*
         * verify
         */
        assertThat(transactional).isFalse();
    }

    // ───────────────────────── join ─────────────────────────

    @DisplayName("join - 정원이 남으면 저장 결과로 응답하고 커밋 후 참여 신호를 보낸다")
    @Test
    void join() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, null, 3L, HOST_ID);
        Party saved = party(PartyStatus.ACTIVE, 3, null, 4L, HOST_ID, MEMBER_ID);
        MemberSummary summary = new MemberSummary(MEMBER_PUBLIC_ID, "참가자", "image.png", null);

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
        assertThat(party.hasMember(MEMBER_ID)).isTrue();
        verify(partyCompletionPolicy, never()).onCompleted(any());
        verify(eventPublisher).publishEvent(eq(new PartyMemberJoinedEvent(PARTY_ID, MEMBER_ID)));

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();

        PartyDetailResult.MemberInfo info = new PartyDetailResult.MemberInfo(MEMBER_PUBLIC_ID, "참가자", "image.png", null, 5, false, false);
        verify(partyChangeNotifier).memberJoined(eq(PARTY_ID), eq(new MemberJoined(info, "ACTIVE", 4L)));
    }

    @DisplayName("join - 정원이 차면 저장 전에 완료 정책을 돌리고 바뀐 상태로 신호를 보낸다")
    @Test
    void join_becomesFull() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 2, null, 0L, HOST_ID);
        Party saved = party(PartyStatus.MATCHING, 2, null, 1L, HOST_ID, MEMBER_ID);

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
        InOrder inOrder = inOrder(partyCompletionPolicy, parties);
        inOrder.verify(partyCompletionPolicy).onCompleted(eq(party));
        inOrder.verify(parties).save(eq(party));
        assertThat(response).isEqualTo(new PartyStatusResponse("MATCHING", 2));

        capturedAfterCommitTask().run();
        ArgumentCaptor<MemberJoined> captor = ArgumentCaptor.forClass(MemberJoined.class);
        verify(partyChangeNotifier).memberJoined(eq(PARTY_ID), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("MATCHING");
        assertThat(captor.getValue().version()).isEqualTo(1L);
    }

    @DisplayName("join - 유저 요약이 없어도 참여되고 신호의 멤버 정보는 null, 탑승 횟수는 0 이다")
    @Test
    void join_noMemberSummary() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID);
        Party saved = party(PartyStatus.ACTIVE, 3, null, 1L, HOST_ID, MEMBER_ID);

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
        PartyDetailResult.MemberInfo info = new PartyDetailResult.MemberInfo(null, null, null, null, 0, false, false);
        verify(partyChangeNotifier).memberJoined(eq(PARTY_ID), eq(new MemberJoined(info, "ACTIVE", 1L)));
    }

    @DisplayName("join - 신호의 host 는 고정값이 아니라 저장 결과의 방장 판별을 따른다")
    @Test
    void join_hostFromSaved() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID);
        Party saved = party(PartyStatus.ACTIVE, 3, null, 1L, MEMBER_ID, HOST_ID);

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
        PartyDetailResult.MemberInfo info = new PartyDetailResult.MemberInfo(null, null, null, null, 0, true, false);
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
        Party party = party(status, 3, null, 0L, HOST_ID);

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
        assertThat(party.hasMember(MEMBER_ID)).isFalse();
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor, partyCompletionPolicy);
    }

    @DisplayName("join - 정원이 찬 ACTIVE 방이면 PARTY_FULL")
    @Test
    void join_partyFull() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 2, null, 0L, HOST_ID, THIRD_ID);

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
        verifyNoInteractions(eventPublisher, afterCommitExecutor, partyCompletionPolicy);
    }

    @DisplayName("join - 이미 그 방의 멤버면 ALREADY_JOINED_PARTY")
    @Test
    void join_alreadyMember() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID, MEMBER_ID);

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
        Party party = party(PartyStatus.ACTIVE, 3, null, 2L, HOST_ID, MEMBER_ID);

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
        assertThat(party.hasMember(MEMBER_ID)).isFalse();
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
        Party party = party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID, MEMBER_ID, THIRD_ID);

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

    @DisplayName("leave - 정원이 찬 COMPLETED 방에서 나가면 모집 중으로 돌아간 상태를 보낸다")
    @Test
    void leave_fromCompleted() {

        /*
         * settings
         */
        Party party = party(PartyStatus.COMPLETED, 2, null, 0L, HOST_ID, MEMBER_ID);

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
        assertThat(party.getCompletedAt()).isNull();
        capturedAfterCommitTask().run();
        verify(partyChangeNotifier).memberLeft(eq(PARTY_ID), eq(MEMBER_ID), eq(new MemberLeft(MEMBER_PUBLIC_ID, "ACTIVE", HOST_PUBLIC_ID, 1L)));
    }

    @DisplayName("leave - 마지막 멤버가 나가면 CANCELED 로 닫히고 퇴장 신호와 방장 조회 없이 closed 만 보낸다")
    @Test
    void leave_lastMember() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID);

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
        Party party = party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID, MEMBER_ID);

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
        Party party = party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID, MEMBER_ID);

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
        Party party = party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID);

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

    @DisplayName("leave - 매칭이 시작된 뒤의 방이면 PARTY_NOT_RECRUITING")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
    void leave_notRecruiting(PartyStatus status) {

        /*
         * settings
         */
        Party party = party(status, 2, null, 0L, HOST_ID, MEMBER_ID);

        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.leave(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_RECRUITING);
        assertThat(party.hasMember(MEMBER_ID)).isTrue();
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor);
    }

    @DisplayName("leave - 약속을 확정한 실시간 방이면 방장도 CANNOT_LEAVE_CONFIRMED_PARTY 이고 저장·이벤트·신호가 없다")
    @ParameterizedTest(name = "memberId={0}")
    @MethodSource("confirmedRoomMembers")
    void leave_confirmedRealtime(Long memberId) {

        /*
         * settings
         */
        Party party = party(RoomType.REALTIME, Instant.now().plus(Duration.ofMinutes(30)), PartyStatus.COMPLETED, 3, 3L, HOST_ID, MEMBER_ID);

        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(memberId)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.leave(PARTY_ID, memberId));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.CANNOT_LEAVE_CONFIRMED_PARTY);
        assertThat(party.getMembers()).hasSize(2);
        assertThat(party.getVersion()).isEqualTo(3L);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("leave - 예약 방이면 참여한 순간부터 방장도 CANNOT_LEAVE_CONFIRMED_PARTY 이고 저장·이벤트·신호가 없다")
    @ParameterizedTest(name = "memberId={0}")
    @MethodSource("confirmedRoomMembers")
    void leave_reservation(Long memberId) {

        /*
         * settings
         */
        Party party = party(RoomType.RESERVED, Instant.now().plus(Duration.ofDays(1)), PartyStatus.ACTIVE, 3, 1L, HOST_ID, MEMBER_ID);

        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(memberId)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.leave(PARTY_ID, memberId));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.CANNOT_LEAVE_CONFIRMED_PARTY);
        assertThat(party.getMembers()).hasSize(2);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    static Stream<Long> confirmedRoomMembers() {
        return Stream.of(HOST_ID, MEMBER_ID);
    }

    // ───────────────────────── getPartyDetail ─────────────────────────

    @DisplayName("getPartyDetail - 멤버별 요약·탑승 횟수·방장 표시를 합쳐 돌려준다")
    @Test
    void getPartyDetail() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, null, 7L, HOST_ID, MEMBER_ID);
        List<Long> memberIds = List.of(HOST_ID, MEMBER_ID);

        doReturn(Optional.of(party)).when(parties).findById(eq(PARTY_ID));
        doReturn(Map.of(HOST_ID, new MemberSummary(HOST_PUBLIC_ID, "방장", "host.png", null),
                MEMBER_ID, new MemberSummary(MEMBER_PUBLIC_ID, "참가자", "member.png", null)))
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
        assertThat(result.taxiDriverId()).isNull();
        assertThat(result.roomType()).isEqualTo("REALTIME");
        assertThat(result.departureAt()).isNull();
        assertThat(result.confirmed()).isFalse();
        assertThat(result.members()).containsExactly(
                new PartyDetailResult.MemberInfo(HOST_PUBLIC_ID, "방장", "host.png", null, 4, true, false),
                new PartyDetailResult.MemberInfo(MEMBER_PUBLIC_ID, "참가자", "member.png", null, 0, false, false));
    }

    @DisplayName("getPartyDetail - 유저 요약이 없는 멤버는 표시 정보가 null 이어도 목록에 남는다")
    @Test
    void getPartyDetail_noMemberSummary() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID);

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
        assertThat(result.members()).containsExactly(new PartyDetailResult.MemberInfo(null, null, null, null, 0, true, false));
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
        Party party = confirmedParty(Instant.parse("2026-11-03T12:00:00Z"), PartyStatus.COMPLETED, null, 3L,
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

    // ───────────────────────── findActiveParties ─────────────────────────

    @DisplayName("findActiveParties - ACTIVE 상태만 조회해 결과로 바꾼다")
    @Test
    void findActiveParties() {

        /*
         * settings
         */
        doReturn(List.of(party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID))).when(parties).findRealtimeByStatus(eq(PartyStatus.ACTIVE));

        /*
         * test
         */
        List<PartyResult> result = partyService.findActiveParties();

        /*
         * verify
         */
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(PARTY_ID);
        assertThat(result.getFirst().status()).isEqualTo("ACTIVE");
    }

    @DisplayName("findActiveParties - 방이 없으면 빈 목록")
    @Test
    void findActiveParties_empty() {

        /*
         * settings
         */
        doReturn(List.of()).when(parties).findRealtimeByStatus(eq(PartyStatus.ACTIVE));

        /*
         * test
         */
        List<PartyResult> result = partyService.findActiveParties();

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    // ───────────────────────── getAssignDriver ─────────────────────────

    @DisplayName("getAssignDriver - 배정된 기사의 차량 요약을 돌려준다")
    @Test
    void getAssignDriver() {

        /*
         * settings
         */
        DriverSummary summary = new DriverSummary(4, "12가3456", "SEDAN");

        doReturn(Optional.of(party(PartyStatus.DRIVER_ASSIGNED, 2, DRIVER_ID, 0L, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));
        doReturn(Optional.of(summary)).when(driverAccess).findSummary(eq(DRIVER_ID));

        /*
         * test
         */
        DriverSummary result = partyService.getAssignDriver(PARTY_ID);

        /*
         * verify
         */
        assertThat(result).isEqualTo(summary);
    }

    @DisplayName("getAssignDriver - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void getAssignDriver_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.getAssignDriver(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(driverAccess);
    }

    @DisplayName("getAssignDriver - 기사가 배정 전이면 DRIVER_NOT_ASSIGNED 이고 기사 조회를 하지 않는다")
    @Test
    void getAssignDriver_notAssigned() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.MATCHING, 2, null, 0L, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.getAssignDriver(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.DRIVER_NOT_ASSIGNED);
        verifyNoInteractions(driverAccess);
    }

    @DisplayName("getAssignDriver - 배정된 기사 정보가 없으면 ASSIGNED_DRIVER_NOT_FOUND")
    @Test
    void getAssignDriver_driverNotFound() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.DRIVER_ASSIGNED, 2, DRIVER_ID, 0L, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));
        doReturn(Optional.empty()).when(driverAccess).findSummary(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.getAssignDriver(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ASSIGNED_DRIVER_NOT_FOUND);
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

    // ───────────────────────── confirm ─────────────────────────

    @DisplayName("confirm - ACTIVE 방을 확정하면 잠금 조회한 방에 약속 시간을 담고 완료 정책을 한 번 돌린 뒤 저장하고 커밋 후 confirmed 를 보낸다")
    @Test
    void confirm() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, null, 2L, HOST_ID, MEMBER_ID);
        Instant departureAt = Instant.now().plus(Duration.ofMinutes(30));

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.confirm(PARTY_ID, HOST_ID, departureAt);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getDepartureAt()).isEqualTo(departureAt);
        assertThat(party.getVersion()).isEqualTo(3L);

        InOrder order = inOrder(parties, partyCompletionPolicy, afterCommitExecutor);
        order.verify(parties).findByIdForUpdate(eq(PARTY_ID));
        order.verify(partyCompletionPolicy, times(1)).onCompleted(eq(party));
        order.verify(parties).save(eq(party));
        order.verify(afterCommitExecutor).execute(eq(SSE_TASK), any());
        verify(parties, never()).findById(any());
        verifyNoInteractions(eventPublisher);

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();

        verify(partyChangeNotifier).confirmed(eq(PARTY_ID), eq(new PartyConfirmed("COMPLETED", departureAt, 3L)));
        verify(partyChangeNotifier, never()).recruitingClosed(any(), any());
    }

    @DisplayName("confirm - 정원이 차 이미 COMPLETED 인 방은 참여 때 돌린 완료 정책을 다시 돌리지 않고 약속 시간만 담는다")
    @Test
    void confirm_alreadyCompleted() {

        /*
         * settings
         */
        Party party = party(PartyStatus.COMPLETED, 2, null, 1L, HOST_ID, MEMBER_ID);
        Instant departureAt = Instant.now().plus(Duration.ofMinutes(30));

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.confirm(PARTY_ID, HOST_ID, departureAt);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getDepartureAt()).isEqualTo(departureAt);
        verifyNoInteractions(partyCompletionPolicy);
        verify(parties).save(eq(party));

        capturedAfterCommitTask().run();
        verify(partyChangeNotifier).confirmed(eq(PARTY_ID), eq(new PartyConfirmed("COMPLETED", departureAt, 2L)));
    }

    @DisplayName("confirm - 완료 정책이 바꾼 상태(기사 호출 모드의 MATCHING)를 신호에 싣는다")
    @Test
    void confirm_policyChangesStatus() {

        /*
         * settings
         */
        Party party = party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID, MEMBER_ID);
        Instant departureAt = Instant.now().plus(Duration.ofMinutes(30));

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doAnswer(invocation -> {
            invocation.<Party>getArgument(0).startMatching(Instant.now());
            return null;
        }).when(partyCompletionPolicy).onCompleted(eq(party));

        /*
         * test
         */
        partyService.confirm(PARTY_ID, HOST_ID, departureAt);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.MATCHING);
        verify(parties).save(eq(party));

        capturedAfterCommitTask().run();
        verify(partyChangeNotifier).confirmed(eq(PARTY_ID), eq(new PartyConfirmed("MATCHING", departureAt, 1L)));
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
        Throwable thrown = catchThrowable(() -> partyService.confirm(PARTY_ID, HOST_ID, Instant.now().plus(Duration.ofMinutes(30))));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verify(parties, never()).save(any());
        verifyNoInteractions(partyCompletionPolicy, eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("confirm - 도메인이 거절하면 그 오류가 나가고 완료 정책·저장·신호가 없다")
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
        Throwable thrown = catchThrowable(() -> partyService.confirm(PARTY_ID, memberId, Instant.now().plus(fromNow)));

        /*
         * verify
         */
        assertBusinessException(thrown, expected);
        verify(parties, never()).save(any());
        verifyNoInteractions(partyCompletionPolicy, eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    static Stream<Arguments> confirmRejections() {
        Duration ok = Duration.ofMinutes(30);
        return Stream.of(
                Arguments.of(MatchingErrorCode.NOT_PARTY_MEMBER, party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID, MEMBER_ID), THIRD_ID, "방에 없는 사람", ok),
                Arguments.of(MatchingErrorCode.NOT_PARTY_HOST, party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID, MEMBER_ID), MEMBER_ID, "방장이 아닌 멤버", ok),
                Arguments.of(MatchingErrorCode.NOT_REALTIME_PARTY, party(RoomType.RESERVED, Instant.now().plus(Duration.ofDays(1)), PartyStatus.ACTIVE, 3, 0L, HOST_ID, MEMBER_ID), HOST_ID, "예약 방", ok),
                Arguments.of(MatchingErrorCode.ALREADY_CONFIRMED, party(RoomType.REALTIME, Instant.now().plus(Duration.ofMinutes(20)), PartyStatus.COMPLETED, 3, 0L, HOST_ID, MEMBER_ID), HOST_ID, "이미 확정한 방", ok),
                Arguments.of(MatchingErrorCode.NOT_ENOUGH_MEMBERS, party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID), HOST_ID, "방장 혼자", ok),
                Arguments.of(MatchingErrorCode.PARTY_NOT_RECRUITING, party(PartyStatus.CANCELED, 3, null, 0L, HOST_ID, MEMBER_ID), HOST_ID, "닫힌 방", ok),
                Arguments.of(MatchingErrorCode.PARTY_NOT_RECRUITING, party(PartyStatus.MATCHING, 2, null, 0L, HOST_ID, MEMBER_ID), HOST_ID, "매칭이 시작된 방", ok),
                Arguments.of(MatchingErrorCode.INVALID_MEETING_TIME, party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID, MEMBER_ID), HOST_ID, "1분 넘게 지난 시각", Duration.ofMinutes(-2)),
                Arguments.of(MatchingErrorCode.INVALID_MEETING_TIME, party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID, MEMBER_ID), HOST_ID, "1시간 넘게 뒤", Duration.ofMinutes(61)));
    }

    // ───────────────────────── arrive ─────────────────────────

    @DisplayName("arrive - 잠금 조회한 방의 출발지 100m 안인지 요청 좌표로 DB 에 묻고, 안이면 도착을 남기고 저장한 뒤 커밋 후 member-arrived 에 publicId 와 오른 version 을 싣는다")
    @Test
    void arrive() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, null, 4L,
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
        verifyNoInteractions(eventPublisher, rideRecords, partyCompletionPolicy);

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();

        verify(partyChangeNotifier).memberArrived(eq(PARTY_ID), eq(new MemberArrived(MEMBER_PUBLIC_ID, 5L)));
    }

    @DisplayName("arrive - 이미 도착한 멤버가 다시 누르면 오류 없이 저장·신호를 하지 않는다")
    @Test
    void arrive_alreadyArrived() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, null, 4L,
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
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, null, 0L,
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
                Arguments.of(MatchingErrorCode.NOT_PARTY_MEMBER, confirmedParty(started, PartyStatus.COMPLETED, null, 0L, waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), THIRD_ID, NEAR_LAT, true, "방에 없는 사람"),
                Arguments.of(MatchingErrorCode.PARTY_NOT_CONFIRMED, party(PartyStatus.COMPLETED, 2, null, 0L, HOST_ID, MEMBER_ID), HOST_ID, NEAR_LAT, true, "약속 확정 전 실시간 방"),
                Arguments.of(MatchingErrorCode.PARTY_CLOSED, confirmedParty(started, PartyStatus.FINISHED, null, 0L, waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, NEAR_LAT, true, "이미 끝난 방"),
                Arguments.of(MatchingErrorCode.MEETING_NOT_STARTED, confirmedParty(Instant.now().plus(Duration.ofMinutes(10)), PartyStatus.COMPLETED, null, 0L, waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, NEAR_LAT, true, "약속 시간 전"),
                Arguments.of(MatchingErrorCode.TOO_FAR_FROM_DEPARTURE, confirmedParty(started, PartyStatus.COMPLETED, null, 0L, waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, FAR_LAT, false, "출발지에서 약 111m - DB 가 반경 밖이라고 답함"));
    }

    // ───────────────────────── depart ─────────────────────────

    @DisplayName("depart - 방을 FINISHED 로 끝내 노쇼를 남겨 저장하고, 기사 없는 하차 기록·닫힘 이벤트를 남긴 뒤 커밋 후 closed 를 보낸다")
    @Test
    void depart() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, null, 6L,
                arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1), arrivedMember(THIRD_ID, 2));

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.depart(PARTY_ID, THIRD_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getVersion()).isEqualTo(7L);
        assertThat(memberOf(party, MEMBER_ID).isNoShow()).isTrue();
        assertThat(memberOf(party, HOST_ID).isNoShow()).isFalse();
        assertThat(memberOf(party, THIRD_ID).isNoShow()).isFalse();

        InOrder order = inOrder(parties, rideRecords, eventPublisher, afterCommitExecutor);
        order.verify(parties).findByIdForUpdate(eq(PARTY_ID));
        order.verify(parties).save(eq(party));
        order.verify(rideRecords).save(any(RideRecord.class));
        order.verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, "FINISHED")));
        order.verify(afterCommitExecutor).execute(eq(SSE_TASK), any());
        verifyNoInteractions(userAccess, partyCompletionPolicy);

        ArgumentCaptor<RideRecord> captor = ArgumentCaptor.forClass(RideRecord.class);
        verify(rideRecords).save(captor.capture());
        RideRecord record = captor.getValue();
        assertThat(record.getMatchId()).isEqualTo(PARTY_ID);
        assertThat(record.getDeparturePlace()).isEqualTo("강남역");
        assertThat(record.getDestinationPlace()).isEqualTo("판교역");
        assertThat(record.getTaxiDriverId()).isNull();
        assertThat(record.getPickupTime()).isNull();
        assertThat(record.getTotalFare()).isNull();
        assertThat(record.isDroppedOff()).isTrue();

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("FINISHED")));
        verify(partyChangeNotifier, never()).memberArrived(any(), any());
    }

    @DisplayName("depart - 모두 도착했으면 노쇼 없이 끝난다")
    @Test
    void depart_everyoneArrived() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, null, 0L,
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
        assertThat(party.getMembers()).noneMatch(PartyMember::isNoShow);
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
        assertThat(party.getMembers()).noneMatch(m -> m.getNoShowAt() != null);
        verify(parties, never()).save(any());
        verifyNoInteractions(rideRecords, eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    static Stream<Arguments> departRejections() {
        Instant started = Instant.now().minus(Duration.ofMinutes(5));
        return Stream.of(
                Arguments.of(MatchingErrorCode.NOT_PARTY_MEMBER, confirmedParty(started, PartyStatus.COMPLETED, null, 0L, arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), THIRD_ID, "방에 없는 사람"),
                Arguments.of(MatchingErrorCode.PARTY_NOT_CONFIRMED, party(PartyStatus.COMPLETED, 2, null, 0L, HOST_ID, MEMBER_ID), HOST_ID, "약속 확정 전 실시간 방"),
                Arguments.of(MatchingErrorCode.PARTY_CLOSED, confirmedParty(started, PartyStatus.FINISHED, null, 0L, arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, "이미 끝난 방"),
                Arguments.of(MatchingErrorCode.PARTY_CLOSED, confirmedParty(started, PartyStatus.CANCELED, null, 0L, arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, "취소된 방"),
                Arguments.of(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED, confirmedParty(started, PartyStatus.COMPLETED, DRIVER_ID, 0L, arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, "기사가 배정된 방"),
                Arguments.of(MatchingErrorCode.MEETING_NOT_STARTED, confirmedParty(Instant.now().plus(Duration.ofMinutes(10)), PartyStatus.COMPLETED, null, 0L, arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), HOST_ID, "약속 시간 전"),
                Arguments.of(MatchingErrorCode.NOT_ARRIVED, confirmedParty(started, PartyStatus.COMPLETED, null, 0L, arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), MEMBER_ID, "누른 사람이 도착 전"));
    }

    // ───────────────────────── finish ─────────────────────────

    @DisplayName("finish - 방장이 COMPLETED 방을 끝내면 기사 없는 하차 기록을 남기고 커밋 후 closed 를 보낸다")
    @Test
    void finish() {

        /*
         * settings
         */
        Party party = party(PartyStatus.COMPLETED, 2, null, 5L, HOST_ID, MEMBER_ID);

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.finish(PARTY_ID, HOST_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getVersion()).isEqualTo(6L);
        verify(parties).save(eq(party));

        ArgumentCaptor<RideRecord> captor = ArgumentCaptor.forClass(RideRecord.class);
        verify(rideRecords).save(captor.capture());
        RideRecord record = captor.getValue();
        assertThat(record.getMatchId()).isEqualTo(PARTY_ID);
        assertThat(record.getDeparturePlace()).isEqualTo("강남역");
        assertThat(record.getDestinationPlace()).isEqualTo("판교역");
        assertThat(record.getTaxiDriverId()).isNull();
        assertThat(record.getPickupTime()).isNull();
        assertThat(record.getTotalFare()).isNull();
        assertThat(record.isDroppedOff()).isTrue();

        verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, "FINISHED")));

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("FINISHED")));
    }

    @DisplayName("finish - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void finish_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.finish(PARTY_ID, HOST_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(rideRecords, eventPublisher, afterCommitExecutor);
    }

    @DisplayName("finish - 도메인이 거절하면 그 오류가 나가고 저장·기록·신호가 없다")
    @ParameterizedTest(name = "{0}")
    @MethodSource("finishRejections")
    void finish_rejected(MatchingErrorCode expected, Party party, Long memberId) {

        /*
         * settings
         */
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.finish(PARTY_ID, memberId));

        /*
         * verify
         */
        assertBusinessException(thrown, expected);
        verify(parties, never()).save(any());
        verifyNoInteractions(rideRecords, eventPublisher, afterCommitExecutor);
    }

    static Stream<Arguments> finishRejections() {
        return Stream.of(
                Arguments.of(MatchingErrorCode.NOT_PARTY_MEMBER, party(PartyStatus.COMPLETED, 2, null, 0L, HOST_ID, THIRD_ID), MEMBER_ID),
                Arguments.of(MatchingErrorCode.NOT_PARTY_HOST, party(PartyStatus.COMPLETED, 2, null, 0L, HOST_ID, MEMBER_ID), MEMBER_ID),
                Arguments.of(MatchingErrorCode.PARTY_NOT_COMPLETED, party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID, MEMBER_ID), HOST_ID),
                Arguments.of(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED, party(PartyStatus.COMPLETED, 2, DRIVER_ID, 0L, HOST_ID, MEMBER_ID), HOST_ID),
                Arguments.of(MatchingErrorCode.USE_DEPART_FOR_CONFIRMED_PARTY, party(RoomType.REALTIME, Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, 2, 0L, HOST_ID, MEMBER_ID), HOST_ID),
                Arguments.of(MatchingErrorCode.USE_DEPART_FOR_CONFIRMED_PARTY, party(RoomType.RESERVED, Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, 2, 0L, HOST_ID, MEMBER_ID), HOST_ID));
    }

    // ───────────────────────── autoClose ─────────────────────────

    @DisplayName("autoClose - 도착한 멤버가 있는 확정 방은 FINISHED 로 닫고 노쇼를 남겨 저장한 뒤 기사 없는 하차 기록·닫힘 이벤트를 남기고 커밋 후 closed 를 보낸다")
    @Test
    void autoClose_someArrived() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofDays(1)), PartyStatus.COMPLETED, null, 6L,
                arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1), arrivedMember(THIRD_ID, 2));

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        List<Long> noShowMemberIds = partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        assertThat(noShowMemberIds).containsExactly(MEMBER_ID);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getVersion()).isEqualTo(7L);
        assertThat(memberOf(party, MEMBER_ID).isNoShow()).isTrue();
        assertThat(memberOf(party, HOST_ID).isNoShow()).isFalse();
        assertThat(memberOf(party, THIRD_ID).isNoShow()).isFalse();

        InOrder order = inOrder(parties, rideRecords, eventPublisher, afterCommitExecutor);
        order.verify(parties).findByIdForUpdate(eq(PARTY_ID));
        order.verify(parties).save(eq(party));
        order.verify(rideRecords).save(any(RideRecord.class));
        order.verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, "FINISHED")));
        order.verify(afterCommitExecutor).execute(eq(SSE_TASK), any());
        verifyNoInteractions(userAccess, partyCompletionPolicy);

        ArgumentCaptor<RideRecord> captor = ArgumentCaptor.forClass(RideRecord.class);
        verify(rideRecords).save(captor.capture());
        RideRecord record = captor.getValue();
        assertThat(record.getMatchId()).isEqualTo(PARTY_ID);
        assertThat(record.getDeparturePlace()).isEqualTo("강남역");
        assertThat(record.getDestinationPlace()).isEqualTo("판교역");
        assertThat(record.getTaxiDriverId()).isNull();
        assertThat(record.isDroppedOff()).isTrue();

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("FINISHED")));
    }

    @DisplayName("autoClose - 아무도 도착하지 않은 확정 방은 전원 노쇼로 CANCELED 이고 하차 기록 없이 닫힘 이벤트와 커밋 후 closed 를 보낸다")
    @Test
    void autoClose_nobodyArrived() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofDays(1)), PartyStatus.COMPLETED, null, 6L,
                waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1));

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        List<Long> noShowMemberIds = partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        assertThat(noShowMemberIds).containsExactly(HOST_ID, MEMBER_ID);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.getMembers()).allMatch(PartyMember::isNoShow);
        verify(parties).save(eq(party));
        verifyNoInteractions(rideRecords);
        verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, "CANCELED")));

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("CANCELED")));
    }

    @DisplayName("autoClose - 약속을 정하지 않은 방은 노쇼 없이 COMPLETED 는 FINISHED, ACTIVE 는 CANCELED 로 닫고 하차 기록을 남기지 않는다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"COMPLETED, FINISHED", "ACTIVE, CANCELED"})
    void autoClose_unconfirmed(PartyStatus before, PartyStatus after) {

        /*
         * settings
         */
        Party party = party(before, 3, null, 5L, HOST_ID, MEMBER_ID);

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        List<Long> noShowMemberIds = partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        assertThat(noShowMemberIds).isEmpty();
        assertThat(party.getStatus()).isEqualTo(after);
        assertThat(party.getVersion()).isEqualTo(6L);
        assertThat(party.getMembers()).noneMatch(m -> m.getNoShowAt() != null);
        verify(parties).save(eq(party));
        verifyNoInteractions(rideRecords);
        verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, after.name())));

        Runnable task = capturedAfterCommitTask();
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed(after.name())));
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

    @DisplayName("autoClose - 도메인이 거절하면 그 오류가 나가고 저장·기록·이벤트·신호가 없다")
    @ParameterizedTest(name = "{0} - {2}")
    @MethodSource("autoCloseRejections")
    void autoClose_rejected(MatchingErrorCode expected, Party party, String reason) {

        /*
         * settings
         */
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.autoClose(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, expected);
        assertThat(party.getMembers()).noneMatch(m -> m.getNoShowAt() != null);
        verify(parties, never()).save(any());
        verifyNoInteractions(rideRecords, eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    static Stream<Arguments> autoCloseRejections() {
        Instant started = Instant.now().minus(Duration.ofDays(1));
        return Stream.of(
                Arguments.of(MatchingErrorCode.PARTY_CLOSED, confirmedParty(started, PartyStatus.FINISHED, null, 0L, arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), "출발로 이미 끝난 방"),
                Arguments.of(MatchingErrorCode.PARTY_CLOSED, party(PartyStatus.CANCELED, 2, null, 0L, HOST_ID), "이미 취소된 방"),
                Arguments.of(MatchingErrorCode.PARTY_CLOSED, party(PartyStatus.MATCHING, 2, null, 0L, HOST_ID, MEMBER_ID), "기사 호출 중인 방"),
                Arguments.of(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED, party(PartyStatus.COMPLETED, 2, DRIVER_ID, 0L, HOST_ID, MEMBER_ID), "기사가 배정된 COMPLETED 방"),
                Arguments.of(MatchingErrorCode.MEETING_NOT_STARTED, confirmedParty(Instant.now().plus(Duration.ofMinutes(30)), PartyStatus.COMPLETED, null, 0L, waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1)), "약속 시간 전"));
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

    // ───────────────────────── 노쇼 제재 ─────────────────────────

    @DisplayName("open - 노쇼로 이용 제한 중이면 NO_SHOW_RESTRICTED 이고 진행 중 방 확인·경로 조회(네이버)·저장을 하지 않는다")
    @Test
    void open_noShowRestricted() {

        /*
         * settings
         */
        doReturn(Optional.of(Instant.now().plus(Duration.ofHours(3)))).when(noShowPenalty).findRestrictedUntil(eq(HOST_ID), any(Instant.class));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.open(openCommand(3)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NO_SHOW_RESTRICTED);
        verifyNoInteractions(parties, routeService, partyOpener);
    }

    @DisplayName("open - 제한 확인을 진행 중 방 확인·경로 조회보다 먼저 한다")
    @Test
    void open_checksRestrictionFirst() {

        /*
         * settings
         */
        OpenPartyCommand command = openCommand(3);
        PartyResult opened = PartyResult.from(party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID));
        doReturn(ESTIMATE).when(routeService).estimate(eq(DEPARTURE_LAT), eq(DEPARTURE_LNG), eq(DESTINATION_LAT), eq(DESTINATION_LNG));
        doReturn(opened).when(partyOpener).open(eq(command), eq(ESTIMATE));

        /*
         * test
         */
        partyService.open(command);

        /*
         * verify
         */
        InOrder order = inOrder(noShowPenalty, parties, routeService, partyOpener);
        order.verify(noShowPenalty).findRestrictedUntil(eq(HOST_ID), any(Instant.class));
        order.verify(parties).existsOngoingRealtimeByMemberId(eq(HOST_ID));
        order.verify(routeService).estimate(eq(DEPARTURE_LAT), eq(DEPARTURE_LNG), eq(DESTINATION_LAT), eq(DESTINATION_LNG));
        order.verify(partyOpener).open(eq(command), eq(ESTIMATE));
    }

    @DisplayName("reserve - 노쇼로 이용 제한 중이면 NO_SHOW_RESTRICTED 이고 PartyOpener 에 맡기지 않는다")
    @Test
    void reserve_noShowRestricted() {

        /*
         * settings
         */
        doReturn(Optional.of(Instant.now().plus(Duration.ofHours(3)))).when(noShowPenalty).findRestrictedUntil(eq(HOST_ID), any(Instant.class));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.reserve(reservationCommand()));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NO_SHOW_RESTRICTED);
        verifyNoInteractions(partyOpener, parties);
    }

    @DisplayName("reserve - 제한이 없으면 제한을 확인한 뒤 PartyOpener 에 맡긴다")
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
        InOrder order = inOrder(noShowPenalty, partyOpener);
        order.verify(noShowPenalty).findRestrictedUntil(eq(HOST_ID), any(Instant.class));
        order.verify(partyOpener).reserve(eq(command));
    }

    @DisplayName("join - 노쇼로 이용 제한 중이면 NO_SHOW_RESTRICTED 이고 사용자 잠금·방 조회 전에 끊는다")
    @Test
    void join_noShowRestricted() {

        /*
         * settings
         */
        doReturn(Optional.of(Instant.now().plus(Duration.ofHours(3)))).when(noShowPenalty).findRestrictedUntil(eq(MEMBER_ID), any(Instant.class));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.join(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NO_SHOW_RESTRICTED);
        verifyNoInteractions(parties, userAccess, eventPublisher, afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("depart - 노쇼로 찍힌 멤버마다 방의 약속 시간으로 제재를 더한다(방 저장 뒤, 같은 시각, userId 오름차순)")
    @Test
    void depart_penalizesNoShows() {

        /*
         * settings
         */
        Instant meetingAt = Instant.now().minus(Duration.ofMinutes(5));
        // THIRD_ID 가 먼저 합류했지만 제재는 userId 오름차순(MEMBER_ID → THIRD_ID)으로 매겨 users 행 잠금 순서를 고정한다
        Party party = confirmedParty(meetingAt, PartyStatus.COMPLETED, null, 6L,
                arrivedMember(HOST_ID, 0), waitingMember(THIRD_ID, 1), waitingMember(MEMBER_ID, 2));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.depart(PARTY_ID, HOST_ID);

        /*
         * verify
         */
        Instant noShowAt = memberOf(party, MEMBER_ID).getNoShowAt();
        assertThat(memberOf(party, THIRD_ID).getNoShowAt()).isEqualTo(noShowAt);

        InOrder order = inOrder(parties, noShowPenalty);
        order.verify(parties).save(eq(party));
        order.verify(noShowPenalty).record(eq(MEMBER_ID), eq(meetingAt), eq(noShowAt));
        order.verify(noShowPenalty).record(eq(THIRD_ID), eq(meetingAt), eq(noShowAt));
        verify(noShowPenalty, never()).record(eq(HOST_ID), any(), any());
        verify(noShowPenalty, never()).cancel(any(), any());
    }

    @DisplayName("depart - 모두 도착했으면 제재가 없다")
    @Test
    void depart_everyoneArrived_noPenalty() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, null, 0L,
                arrivedMember(HOST_ID, 0), arrivedMember(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.depart(PARTY_ID, HOST_ID);

        /*
         * verify
         */
        verifyNoInteractions(noShowPenalty);
    }

    @DisplayName("depart - 출발이 거절되면(누른 사람이 도착 전) 노쇼도 제재도 없다")
    @Test
    void depart_rejected_noPenalty() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, null, 0L,
                arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.depart(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_ARRIVED);
        verifyNoInteractions(noShowPenalty);
    }

    @DisplayName("autoClose - 노쇼로 찍힌 멤버마다 방의 약속 시간으로 제재를 더한다")
    @Test
    void autoClose_penalizesNoShows() {

        /*
         * settings
         */
        Instant meetingAt = Instant.now().minus(Duration.ofDays(1));
        Party party = confirmedParty(meetingAt, PartyStatus.COMPLETED, null, 6L,
                arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1), waitingMember(THIRD_ID, 2));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        List<Long> noShowMemberIds = partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        assertThat(noShowMemberIds).containsExactly(MEMBER_ID, THIRD_ID);
        Instant noShowAt = memberOf(party, MEMBER_ID).getNoShowAt();

        InOrder order = inOrder(parties, noShowPenalty);
        order.verify(parties).save(eq(party));
        order.verify(noShowPenalty).record(eq(MEMBER_ID), eq(meetingAt), eq(noShowAt));
        order.verify(noShowPenalty).record(eq(THIRD_ID), eq(meetingAt), eq(noShowAt));
        verify(noShowPenalty, never()).record(eq(HOST_ID), any(), any());
    }

    @DisplayName("autoClose - 예약 방은 출발 시각을 약속 시간으로 제재한다(전원 노쇼면 전원)")
    @Test
    void autoClose_reservation_penalizesWithDepartureAt() {

        /*
         * settings
         */
        Instant departureAt = Instant.now().minus(Duration.ofDays(1));
        Party party = party(RoomType.RESERVED, departureAt, PartyStatus.COMPLETED, 3, 2L, HOST_ID, MEMBER_ID);
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        verify(noShowPenalty).record(eq(HOST_ID), eq(departureAt), any(Instant.class));
        verify(noShowPenalty).record(eq(MEMBER_ID), eq(departureAt), any(Instant.class));
    }

    @DisplayName("autoClose - 약속을 정하지 않은 방은 노쇼가 없어 제재도 없다")
    @Test
    void autoClose_unconfirmed_noPenalty() {

        /*
         * settings
         */
        Party party = party(PartyStatus.COMPLETED, 3, null, 0L, HOST_ID, MEMBER_ID);
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        verifyNoInteractions(noShowPenalty);
    }

    // ───────────────────────── 노쇼 알림 ─────────────────────────

    @DisplayName("depart - 노쇼로 찍힌 멤버를 찍은 시각과 함께 노쇼 알림에 넘긴다(방 저장·제재 뒤)")
    @Test
    void depart_notifiesNoShows() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, null, 6L,
                arrivedMember(HOST_ID, 0), waitingMember(THIRD_ID, 1), waitingMember(MEMBER_ID, 2));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.depart(PARTY_ID, HOST_ID);

        /*
         * verify
         */
        Instant noShowAt = memberOf(party, MEMBER_ID).getNoShowAt();
        InOrder order = inOrder(parties, noShowPenalty, meetingNotificationService);
        order.verify(parties).save(eq(party));
        order.verify(noShowPenalty).record(eq(MEMBER_ID), any(), eq(noShowAt));
        order.verify(meetingNotificationService).notifyNoShows(eq(PARTY_ID), eq(List.of(THIRD_ID, MEMBER_ID)), eq(noShowAt));
    }

    @DisplayName("depart - 모두 도착했으면 빈 노쇼 목록을 넘긴다(알림 서비스가 보내지 않는다)")
    @Test
    void depart_everyoneArrived_emptyNoShowNotice() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, null, 0L,
                arrivedMember(HOST_ID, 0), arrivedMember(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.depart(PARTY_ID, HOST_ID);

        /*
         * verify
         */
        verify(meetingNotificationService).notifyNoShows(eq(PARTY_ID), eq(List.of()), any(Instant.class));
    }

    @DisplayName("depart - 출발이 거절되면 노쇼 알림도 맡기지 않는다")
    @Test
    void depart_rejected_noNoShowNotice() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofMinutes(5)), PartyStatus.COMPLETED, null, 0L,
                arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.depart(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_ARRIVED);
        verifyNoInteractions(meetingNotificationService);
    }

    @DisplayName("autoClose - 노쇼로 찍힌 멤버를 찍은 시각과 함께 노쇼 알림에 넘긴다(전원 노쇼면 전원)")
    @Test
    void autoClose_notifiesNoShows() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofDays(1)), PartyStatus.COMPLETED, null, 6L,
                waitingMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        Instant noShowAt = memberOf(party, HOST_ID).getNoShowAt();
        InOrder order = inOrder(parties, meetingNotificationService);
        order.verify(parties).save(eq(party));
        order.verify(meetingNotificationService).notifyNoShows(eq(PARTY_ID), eq(List.of(HOST_ID, MEMBER_ID)), eq(noShowAt));
    }

    @DisplayName("autoClose - 약속을 정하지 않은 방은 빈 노쇼 목록을 넘긴다")
    @Test
    void autoClose_unconfirmed_emptyNoShowNotice() {

        /*
         * settings
         */
        Party party = party(PartyStatus.COMPLETED, 3, null, 0L, HOST_ID, MEMBER_ID);
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.autoClose(PARTY_ID);

        /*
         * verify
         */
        verify(meetingNotificationService).notifyNoShows(eq(PARTY_ID), eq(List.of()), any(Instant.class));
    }

    @DisplayName("autoClose - 조회 뒤 이미 닫힌 방은 거절되고 노쇼 알림도 맡기지 않는다")
    @Test
    void autoClose_rejected_noNoShowNotice() {

        /*
         * settings
         */
        Party party = confirmedParty(Instant.now().minus(Duration.ofDays(1)), PartyStatus.FINISHED, null, 6L,
                arrivedMember(HOST_ID, 0), waitingMember(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.autoClose(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_CLOSED);
        verifyNoInteractions(meetingNotificationService);
    }

    // ───────────────────────── 공통 도우미 ─────────────────────────

    private static OpenPartyCommand openCommand(int capacity) {
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
        return new MemberSummary(publicId, "닉네임", "image.png", null);
    }

    /** memberIds 앞쪽일수록 먼저 합류한 멤버다 - 첫 번째가 방장 */
    private static Party party(PartyStatus status, int capacity, Long taxiDriverId, long version, Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for (int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], BASE_TIME.plusSeconds(i)));
        }

        return Party.load(PARTY_ID, DEPARTURE, DESTINATION, Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역", new Capacity(capacity), members,
                BASE_TIME, status, 12000, 25, "encoded-path", taxiDriverId, null, null, version);
    }

    /** 방 종류와 약속(출발) 시각까지 정해 복원한 방. memberIds 앞쪽일수록 먼저 합류했고 첫 번째가 방장 */
    private static Party party(RoomType roomType, Instant departureAt, PartyStatus status, int capacity, long version, Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for (int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], BASE_TIME.plusSeconds(i)));
        }

        boolean realtime = roomType == RoomType.REALTIME;

        return Party.load(PARTY_ID, DEPARTURE, DESTINATION, Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역", new Capacity(capacity), members,
                memberIds[0], BASE_TIME, status, realtime ? 12000 : null, realtime ? 25 : null, realtime ? "encoded-path" : null,
                null, null, null, version, roomType, departureAt, null);
    }

    /** 방장이 departureAt 으로 약속을 확정한 실시간 방. members 앞쪽이 먼저 합류했고 첫 번째가 방장 */
    private static Party confirmedParty(Instant departureAt, PartyStatus status, Long taxiDriverId, long version, PartyMember... members) {
        return Party.load(PARTY_ID, DEPARTURE, DESTINATION, Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역", new Capacity(3),
                new ArrayList<>(List.of(members)), members[0].getMemberId(), BASE_TIME, status, 12000, 25, "encoded-path",
                taxiDriverId, null, null, version, RoomType.REALTIME, departureAt, null);
    }

    /** 아직 도착하지 않은 멤버. order 가 작을수록 먼저 합류했다 */
    private static PartyMember waitingMember(Long memberId, int order) {
        return PartyMember.load(memberId, BASE_TIME.plusSeconds(order));
    }

    /** 출발지에 도착한 멤버 */
    private static PartyMember arrivedMember(Long memberId, int order) {
        return PartyMember.load(memberId, BASE_TIME.plusSeconds(order), BASE_TIME.plusSeconds(3600), null, null);
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
