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
import team.codingforest.moyeota.matching.party.dto.MatchCandidate;
import team.codingforest.moyeota.matching.party.dto.MemberJoined;
import team.codingforest.moyeota.matching.party.dto.MemberLeft;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;
import team.codingforest.moyeota.matching.party.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.party.dto.PartyClosed;
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(HOST_ID));
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
        order.verify(parties).existsOngoingByMemberId(eq(HOST_ID));
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
        doReturn(true).when(parties).existsOngoingByMemberId(eq(HOST_ID));

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
        doReturn(false).when(parties).existsOngoingByMemberId(eq(HOST_ID));
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(HOST_ID));
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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

        PartyDetailResult.MemberInfo info = new PartyDetailResult.MemberInfo(MEMBER_PUBLIC_ID, "참가자", "image.png", null, 5, false);
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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
        PartyDetailResult.MemberInfo info = new PartyDetailResult.MemberInfo(null, null, null, null, 0, false);
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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
        PartyDetailResult.MemberInfo info = new PartyDetailResult.MemberInfo(null, null, null, null, 0, true);
        verify(partyChangeNotifier).memberJoined(eq(PARTY_ID), eq(new MemberJoined(info, "ACTIVE", 1L)));
    }

    @DisplayName("join - 진행 중인 방이 있으면 ALREADY_JOINED_OTHER_PARTY 이고 뒤 단계로 가지 않는다")
    @Test
    void join_alreadyInOngoingParty() {

        /*
         * settings
         */
        doReturn(true).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));

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

    @DisplayName("join - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void join_partyNotFound() {

        /*
         * settings
         */
        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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
        assertThat(result.members()).containsExactly(
                new PartyDetailResult.MemberInfo(HOST_PUBLIC_ID, "방장", "host.png", null, 4, true),
                new PartyDetailResult.MemberInfo(MEMBER_PUBLIC_ID, "참가자", "member.png", null, 0, false));
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
        assertThat(result.members()).containsExactly(new PartyDetailResult.MemberInfo(null, null, null, null, 0, true));
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

    // ───────────────────────── findActiveParties ─────────────────────────

    @DisplayName("findActiveParties - ACTIVE 상태만 조회해 결과로 바꾼다")
    @Test
    void findActiveParties() {

        /*
         * settings
         */
        doReturn(List.of(party(PartyStatus.ACTIVE, 3, null, 0L, HOST_ID))).when(parties).findAllByStatus(eq(PartyStatus.ACTIVE));

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
        doReturn(List.of()).when(parties).findAllByStatus(eq(PartyStatus.ACTIVE));

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
                Arguments.of(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED, party(PartyStatus.COMPLETED, 2, DRIVER_ID, 0L, HOST_ID, MEMBER_ID), HOST_ID));
    }

    // ───────────────────────── expire ─────────────────────────

    @DisplayName("expire - COMPLETED 방을 FINISHED 로 닫고 하차 기록 없이 커밋 후 closed 를 보낸다")
    @Test
    void expire() {

        /*
         * settings
         */
        Party party = party(PartyStatus.COMPLETED, 2, null, 5L, HOST_ID, MEMBER_ID);

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyService.expire(PARTY_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getVersion()).isEqualTo(6L);
        verify(parties).save(eq(party));
        verifyNoInteractions(rideRecords);
        verify(eventPublisher).publishEvent(eq(new PartyClosedEvent(PARTY_ID, "FINISHED")));

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("FINISHED")));
    }

    @DisplayName("expire - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void expire_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.expire(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor);
    }

    @DisplayName("expire - COMPLETED 가 아니면 PARTY_NOT_COMPLETED 이고 저장·신호가 없다")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void expire_notCompleted(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, 2, null, 0L, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.expire(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_COMPLETED);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor);
    }

    @DisplayName("expire - 기사가 배정된 COMPLETED 방이면 DRIVER_ALREADY_ASSIGNED")
    @Test
    void expire_driverAssigned() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.COMPLETED, 2, DRIVER_ID, 0L, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyService.expire(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, afterCommitExecutor);
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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

        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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
        doReturn(true).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));

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
        verify(parties, never()).existsOngoingByMemberId(anyLong());
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
        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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
        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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
        doReturn(false).when(parties).existsOngoingByMemberId(eq(MEMBER_ID));
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

    private static OpenPartyCommand openCommand(int capacity) {
        return new OpenPartyCommand(HOST_ID, DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG, "강남역", "판교역", capacity);
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
