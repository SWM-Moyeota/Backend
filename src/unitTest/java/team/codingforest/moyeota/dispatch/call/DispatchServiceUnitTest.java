package team.codingforest.moyeota.dispatch.call;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.dispatch.call.domain.CallCandidates;
import team.codingforest.moyeota.dispatch.call.domain.CallNotifier;
import team.codingforest.moyeota.dispatch.exception.DispatchErrorCode;
import team.codingforest.moyeota.dispatch.location.domain.DriverLocations;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartySummary;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DispatchServiceUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long DRIVER_ID = 9L;
    private static final Long OTHER_DRIVER_ID = 10L;
    private static final Long THIRD_DRIVER_ID = 11L;
    private static final Long DRIVER_USER_ID = 77L;
    private static final double DEPARTURE_LATITUDE = 37.4979;
    private static final double DEPARTURE_LONGITUDE = 127.0276;

    @InjectMocks
    private DispatchService dispatchService;
    @Mock
    private PartyAccess partyAccess;
    @Mock
    private DriverAccess driverAccess;
    @Mock
    private DriverLocations driverLocations;
    @Mock
    private CallNotifier callNotifier;
    @Mock
    private CallCandidates callCandidates;

    @DisplayName("dispatch - 초기 반경 1000m 로 근처 기사를 찾아 콜을 보낸다")
    @Test
    void dispatch() {

        /*
         * settings
         */
        PartySummary party = party(null);

        doReturn(Optional.of(party)).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(List.of(DRIVER_ID)).when(driverLocations).findNearby(eq(DEPARTURE_LATITUDE), eq(DEPARTURE_LONGITUDE), eq(1000));
        doReturn(List.of()).when(callCandidates).findAll(eq(PARTY_ID));
        doReturn(List.of()).when(callCandidates).findRejected(eq(PARTY_ID));
        doReturn(true).when(driverAccess).canReceiveCalls(eq(DRIVER_ID));

        /*
         * test
         */
        dispatchService.dispatch(PARTY_ID);

        /*
         * verify
         */
        verify(driverLocations).findNearby(eq(DEPARTURE_LATITUDE), eq(DEPARTURE_LONGITUDE), eq(1000));
        verify(callCandidates).add(eq(PARTY_ID), eq(List.of(DRIVER_ID)));
        verify(callNotifier).notifyCall(eq(List.of(DRIVER_ID)), eq(party));
    }

    @DisplayName("dispatch - 방이 없으면 PARTY_NOT_FOUND")
    @Test
    void dispatch_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(partyAccess).findSummary(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> dispatchService.dispatch(PARTY_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(driverLocations, callCandidates, callNotifier, driverAccess);
    }

    @DisplayName("dispatch - 이미 기사가 배정된 방이면 호출을 건너뛴다")
    @Test
    void dispatch_alreadyAssigned() {

        /*
         * settings
         */
        doReturn(Optional.of(party(DRIVER_ID))).when(partyAccess).findSummary(eq(PARTY_ID));

        /*
         * test
         */
        dispatchService.dispatch(PARTY_ID);

        /*
         * verify
         */
        verifyNoInteractions(driverLocations, callCandidates, callNotifier, driverAccess);
    }

    @DisplayName("acceptCall - 성공하면 배정하고 후보를 비운 뒤 나머지 기사에게 마감을 알린다")
    @Test
    void acceptCall() {

        /*
         * settings
         */
        stubAcceptable();
        doReturn(List.of(DRIVER_ID, OTHER_DRIVER_ID, THIRD_DRIVER_ID)).when(callCandidates).findAll(eq(PARTY_ID));

        /*
         * test
         */
        dispatchService.acceptCall(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        InOrder order = inOrder(partyAccess, callCandidates, driverLocations, callNotifier);
        order.verify(partyAccess).assignDriver(eq(PARTY_ID), eq(DRIVER_ID));
        order.verify(callCandidates).findAll(eq(PARTY_ID));     // 비우기 전에 읽어야 마감 알림 대상을 알 수 있다
        order.verify(callCandidates).clear(eq(PARTY_ID));
        order.verify(driverLocations).remove(eq(DRIVER_ID));
        order.verify(callNotifier).notifyCallClosed(eq(List.of(OTHER_DRIVER_ID, THIRD_DRIVER_ID)), eq(PARTY_ID));
    }

    @DisplayName("acceptCall - 후보가 수락한 기사뿐이면 빈 목록으로 마감을 알린다")
    @Test
    void acceptCall_noLosers() {

        /*
         * settings
         */
        stubAcceptable();
        doReturn(List.of(DRIVER_ID)).when(callCandidates).findAll(eq(PARTY_ID));

        /*
         * test
         */
        dispatchService.acceptCall(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        verify(partyAccess).assignDriver(eq(PARTY_ID), eq(DRIVER_ID));
        verify(callCandidates).clear(eq(PARTY_ID));
        verify(callNotifier).notifyCallClosed(eq(List.of()), eq(PARTY_ID));
    }

    @DisplayName("acceptCall - 후보가 아니면 CALL_CLOSED 이고 잠금·배정으로 넘어가지 않는다")
    @Test
    void acceptCall_notCandidate() {

        /*
         * settings
         */
        doReturn(false).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> dispatchService.acceptCall(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.CALL_CLOSED);
        verifyNoInteractions(driverAccess, partyAccess, driverLocations, callNotifier);
        verify(callCandidates, never()).clear(any());
    }

    @DisplayName("acceptCall - 콜을 받을 수 없는 기사면 DRIVER_CANNOT_RECEIVE 이고 배정하지 않는다")
    @Test
    void acceptCall_cannotReceive() {

        /*
         * settings
         */
        doReturn(true).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));
        doReturn(false).when(driverAccess).lockAndCanReceiveCalls(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> dispatchService.acceptCall(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.DRIVER_CANNOT_RECEIVE);
        verifyNoInteractions(partyAccess, driverLocations, callNotifier);
        verify(driverAccess, never()).findUserId(any());
        verify(callCandidates, never()).clear(any());
    }

    @DisplayName("acceptCall - 이미 운행 중인 기사면 DRIVER_ALREADY_RIDING 이고 배정하지 않는다")
    @Test
    void acceptCall_alreadyRiding() {

        /*
         * settings
         */
        doReturn(true).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));
        doReturn(true).when(driverAccess).lockAndCanReceiveCalls(eq(DRIVER_ID));
        doReturn(true).when(partyAccess).hasOngoingRide(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> dispatchService.acceptCall(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.DRIVER_ALREADY_RIDING);
        verify(driverAccess, never()).findUserId(any());
        verify(partyAccess, never()).assignDriver(any(), any());
        verify(callCandidates, never()).clear(any());
        verifyNoInteractions(driverLocations, callNotifier);
    }

    @DisplayName("acceptCall - 기사 본인이 참여한 방이면 SELF_DISPATCH_NOT_ALLOWED 이고 배정하지 않는다")
    @Test
    void acceptCall_selfDispatch() {

        /*
         * settings
         */
        doReturn(true).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));
        doReturn(true).when(driverAccess).lockAndCanReceiveCalls(eq(DRIVER_ID));
        doReturn(false).when(partyAccess).hasOngoingRide(eq(DRIVER_ID));
        doReturn(Optional.of(DRIVER_USER_ID)).when(driverAccess).findUserId(eq(DRIVER_ID));
        doReturn(true).when(partyAccess).hasMemberOnParty(eq(PARTY_ID), eq(DRIVER_USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> dispatchService.acceptCall(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.SELF_DISPATCH_NOT_ALLOWED);
        verify(partyAccess, never()).assignDriver(any(), any());
        verify(callCandidates, never()).clear(any());
        verifyNoInteractions(driverLocations, callNotifier);
    }

    @DisplayName("acceptCall - 기사의 유저를 찾지 못하면 방 구성원 확인을 건너뛰고 배정한다")
    @Test
    void acceptCall_userNotFound() {

        /*
         * settings
         */
        doReturn(true).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));
        doReturn(true).when(driverAccess).lockAndCanReceiveCalls(eq(DRIVER_ID));
        doReturn(false).when(partyAccess).hasOngoingRide(eq(DRIVER_ID));
        doReturn(Optional.empty()).when(driverAccess).findUserId(eq(DRIVER_ID));
        doReturn(List.of(DRIVER_ID)).when(callCandidates).findAll(eq(PARTY_ID));

        /*
         * test
         */
        dispatchService.acceptCall(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        verify(partyAccess, never()).hasMemberOnParty(any(), any());
        verify(partyAccess).assignDriver(eq(PARTY_ID), eq(DRIVER_ID));
    }

    @DisplayName("acceptCall - 배정이 실패하면 예외를 그대로 던지고 후보를 비우거나 알리지 않는다")
    @Test
    void acceptCall_assignFailed() {

        /*
         * settings
         */
        stubAcceptable();
        doThrow(new BusinessException(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED)).when(partyAccess).assignDriver(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> dispatchService.acceptCall(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);
        verify(callCandidates, never()).findAll(any());
        verify(callCandidates, never()).clear(any());
        verifyNoInteractions(driverLocations, callNotifier);
    }

    @DisplayName("rejectCall - 성공하면 후보에서 빼고 거절로 표시한다")
    @Test
    void rejectCall() {

        /*
         * settings
         */
        doReturn(true).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));
        doReturn(2L).when(callCandidates).remove(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        dispatchService.rejectCall(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        verify(callCandidates).remove(eq(PARTY_ID), eq(DRIVER_ID));
        verify(callCandidates).markRejected(eq(PARTY_ID), eq(DRIVER_ID));
    }

    @DisplayName("rejectCall - 마지막 후보가 거절해 남은 후보가 0명이어도 거절로 표시한다")
    @Test
    void rejectCall_lastCandidate() {

        /*
         * settings
         */
        doReturn(true).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));
        doReturn(0L).when(callCandidates).remove(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        dispatchService.rejectCall(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        verify(callCandidates).markRejected(eq(PARTY_ID), eq(DRIVER_ID));
    }

    @DisplayName("rejectCall - 후보가 아니면 CALL_CLOSED 이고 거절로 표시하지 않는다")
    @Test
    void rejectCall_notCandidate() {

        /*
         * settings
         */
        doReturn(false).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> dispatchService.rejectCall(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.CALL_CLOSED);
        verify(callCandidates, never()).remove(any(), any());
        verify(callCandidates, never()).markRejected(any(), any());
    }

    @DisplayName("attempt - 반경과 출발지 좌표로 찾은 신규 기사를 찾은 순서대로 후보에 넣고 콜을 보낸다")
    @Test
    void attempt() {

        /*
         * settings
         */
        PartySummary party = party(null);

        doReturn(Optional.of(party)).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(List.of(OTHER_DRIVER_ID, DRIVER_ID)).when(driverLocations).findNearby(eq(DEPARTURE_LATITUDE), eq(DEPARTURE_LONGITUDE), eq(2500));
        doReturn(List.of()).when(callCandidates).findAll(eq(PARTY_ID));
        doReturn(List.of()).when(callCandidates).findRejected(eq(PARTY_ID));
        doReturn(true).when(driverAccess).canReceiveCalls(eq(DRIVER_ID));
        doReturn(true).when(driverAccess).canReceiveCalls(eq(OTHER_DRIVER_ID));

        /*
         * test
         */
        dispatchService.attempt(PARTY_ID, 2500);

        /*
         * verify
         */
        InOrder order = inOrder(callCandidates, callNotifier);
        order.verify(callCandidates).add(eq(PARTY_ID), eq(List.of(OTHER_DRIVER_ID, DRIVER_ID)));
        order.verify(callNotifier).notifyCall(eq(List.of(OTHER_DRIVER_ID, DRIVER_ID)), eq(party));
    }

    @DisplayName("attempt - 방이 없으면 PARTY_NOT_FOUND")
    @Test
    void attempt_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(partyAccess).findSummary(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> dispatchService.attempt(PARTY_ID, 1000));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(driverLocations, callCandidates, callNotifier, driverAccess);
    }

    @DisplayName("attempt - 이미 기사가 배정됐으면 근처 기사를 찾지 않는다")
    @Test
    void attempt_alreadyAssigned() {

        /*
         * settings
         */
        doReturn(Optional.of(party(OTHER_DRIVER_ID))).when(partyAccess).findSummary(eq(PARTY_ID));

        /*
         * test
         */
        dispatchService.attempt(PARTY_ID, 1000);

        /*
         * verify
         */
        verifyNoInteractions(driverLocations, callCandidates, callNotifier, driverAccess);
    }

    @DisplayName("attempt - 근처에 기사가 없으면 후보에 넣거나 콜을 보내지 않는다")
    @Test
    void attempt_noNearbyDrivers() {

        /*
         * settings
         */
        doReturn(Optional.of(party(null))).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(List.of()).when(driverLocations).findNearby(eq(DEPARTURE_LATITUDE), eq(DEPARTURE_LONGITUDE), eq(1000));
        doReturn(List.of(OTHER_DRIVER_ID)).when(callCandidates).findAll(eq(PARTY_ID));
        doReturn(List.of()).when(callCandidates).findRejected(eq(PARTY_ID));

        /*
         * test
         */
        dispatchService.attempt(PARTY_ID, 1000);

        /*
         * verify
         */
        verify(callCandidates, never()).add(any(), anyList());
        verifyNoInteractions(callNotifier, driverAccess);
    }

    @DisplayName("attempt - 이미 콜을 받은 기사는 자격 확인 없이 제외한다")
    @Test
    void attempt_alreadyCandidate() {

        /*
         * settings
         */
        PartySummary party = party(null);

        doReturn(Optional.of(party)).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(List.of(DRIVER_ID, OTHER_DRIVER_ID)).when(driverLocations).findNearby(eq(DEPARTURE_LATITUDE), eq(DEPARTURE_LONGITUDE), eq(1000));
        doReturn(List.of(OTHER_DRIVER_ID)).when(callCandidates).findAll(eq(PARTY_ID));
        doReturn(List.of()).when(callCandidates).findRejected(eq(PARTY_ID));
        doReturn(true).when(driverAccess).canReceiveCalls(eq(DRIVER_ID));

        /*
         * test
         */
        dispatchService.attempt(PARTY_ID, 1000);

        /*
         * verify
         */
        verify(driverAccess, never()).canReceiveCalls(eq(OTHER_DRIVER_ID));
        verify(callCandidates).add(eq(PARTY_ID), eq(List.of(DRIVER_ID)));
        verify(callNotifier).notifyCall(eq(List.of(DRIVER_ID)), eq(party));
    }

    @DisplayName("attempt - 거절한 기사는 자격 확인 없이 제외한다")
    @Test
    void attempt_rejectedDriver() {

        /*
         * settings
         */
        PartySummary party = party(null);

        doReturn(Optional.of(party)).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(List.of(DRIVER_ID, OTHER_DRIVER_ID)).when(driverLocations).findNearby(eq(DEPARTURE_LATITUDE), eq(DEPARTURE_LONGITUDE), eq(1000));
        doReturn(List.of()).when(callCandidates).findAll(eq(PARTY_ID));
        doReturn(List.of(OTHER_DRIVER_ID)).when(callCandidates).findRejected(eq(PARTY_ID));
        doReturn(true).when(driverAccess).canReceiveCalls(eq(DRIVER_ID));

        /*
         * test
         */
        dispatchService.attempt(PARTY_ID, 1000);

        /*
         * verify
         */
        verify(driverAccess, never()).canReceiveCalls(eq(OTHER_DRIVER_ID));
        verify(callCandidates).add(eq(PARTY_ID), eq(List.of(DRIVER_ID)));
        verify(callNotifier).notifyCall(eq(List.of(DRIVER_ID)), eq(party));
    }

    @DisplayName("attempt - 콜을 받을 수 없는 기사는 제외한다")
    @Test
    void attempt_cannotReceiveDriver() {

        /*
         * settings
         */
        PartySummary party = party(null);

        doReturn(Optional.of(party)).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(List.of(DRIVER_ID, OTHER_DRIVER_ID)).when(driverLocations).findNearby(eq(DEPARTURE_LATITUDE), eq(DEPARTURE_LONGITUDE), eq(1000));
        doReturn(List.of()).when(callCandidates).findAll(eq(PARTY_ID));
        doReturn(List.of()).when(callCandidates).findRejected(eq(PARTY_ID));
        doReturn(true).when(driverAccess).canReceiveCalls(eq(DRIVER_ID));
        doReturn(false).when(driverAccess).canReceiveCalls(eq(OTHER_DRIVER_ID));

        /*
         * test
         */
        dispatchService.attempt(PARTY_ID, 1000);

        /*
         * verify
         */
        verify(callCandidates).add(eq(PARTY_ID), eq(List.of(DRIVER_ID)));
        verify(callNotifier).notifyCall(eq(List.of(DRIVER_ID)), eq(party));
    }

    @DisplayName("attempt - 필터를 거쳐 신규 후보가 한 명도 남지 않으면 콜을 보내지 않는다")
    @Test
    void attempt_noFreshDrivers() {

        /*
         * settings
         */
        doReturn(Optional.of(party(null))).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(List.of(DRIVER_ID, OTHER_DRIVER_ID, THIRD_DRIVER_ID)).when(driverLocations).findNearby(eq(DEPARTURE_LATITUDE), eq(DEPARTURE_LONGITUDE), eq(1000));
        doReturn(List.of(DRIVER_ID)).when(callCandidates).findAll(eq(PARTY_ID));
        doReturn(List.of(OTHER_DRIVER_ID)).when(callCandidates).findRejected(eq(PARTY_ID));
        doReturn(false).when(driverAccess).canReceiveCalls(eq(THIRD_DRIVER_ID));

        /*
         * test
         */
        dispatchService.attempt(PARTY_ID, 1000);

        /*
         * verify
         */
        verify(callCandidates, never()).add(any(), anyList());
        verifyNoInteractions(callNotifier);
    }

    @DisplayName("attempt - 위치 조회가 실패하면 예외를 그대로 던지고 콜을 보내지 않는다")
    @Test
    void attempt_findNearbyFailed() {

        /*
         * settings
         */
        doReturn(Optional.of(party(null))).when(partyAccess).findSummary(eq(PARTY_ID));
        doThrow(new IllegalStateException("redis down")).when(driverLocations).findNearby(anyDouble(), anyDouble(), anyInt());

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> dispatchService.attempt(PARTY_ID, 1000));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("redis down");
        verifyNoInteractions(callCandidates, callNotifier);
    }

    @DisplayName("radiusFor - 0초면 초기 반경 1000m")
    @Test
    void radiusFor_zero() {

        /*
         * test
         */
        int radius = dispatchService.radiusFor(Duration.ZERO);

        /*
         * verify
         */
        assertThat(radius).isEqualTo(1000);
    }

    @DisplayName("radiusFor - 30초를 다 채우지 못하면 아직 초기 반경이다")
    @Test
    void radiusFor_justBeforeFirstStep() {

        /*
         * test
         */
        int radius = dispatchService.radiusFor(Duration.ofSeconds(29).plusMillis(999));

        /*
         * verify
         */
        assertThat(radius).isEqualTo(1000);
    }

    @DisplayName("radiusFor - 30초가 되면 500m 넓어진다")
    @Test
    void radiusFor_firstStep() {

        /*
         * test
         */
        int radius = dispatchService.radiusFor(Duration.ofSeconds(30));

        /*
         * verify
         */
        assertThat(radius).isEqualTo(1500);
    }

    @DisplayName("radiusFor - 다음 단계 직전인 59초는 1500m")
    @Test
    void radiusFor_justBeforeSecondStep() {

        /*
         * test
         */
        int radius = dispatchService.radiusFor(Duration.ofSeconds(59));

        /*
         * verify
         */
        assertThat(radius).isEqualTo(1500);
    }

    @DisplayName("radiusFor - 상한 직전인 119초는 2500m")
    @Test
    void radiusFor_justBeforeMax() {

        /*
         * test
         */
        int radius = dispatchService.radiusFor(Duration.ofSeconds(119));

        /*
         * verify
         */
        assertThat(radius).isEqualTo(2500);
    }

    @DisplayName("radiusFor - 120초가 되면 상한 3000m")
    @Test
    void radiusFor_max() {

        /*
         * test
         */
        int radius = dispatchService.radiusFor(Duration.ofSeconds(120));

        /*
         * verify
         */
        assertThat(radius).isEqualTo(3000);
    }

    @DisplayName("radiusFor - 150초에도 3500m 가 아니라 상한 3000m")
    @Test
    void radiusFor_overMax() {

        /*
         * test
         */
        int radius = dispatchService.radiusFor(Duration.ofSeconds(150));

        /*
         * verify
         */
        assertThat(radius).isEqualTo(3000);
    }

    @DisplayName("radiusFor - 아주 오래 지나도 상한 3000m")
    @Test
    void radiusFor_veryLong() {

        /*
         * test
         */
        int radius = dispatchService.radiusFor(Duration.ofDays(3650));

        /*
         * verify
         */
        assertThat(radius).isEqualTo(3000);
    }

    @DisplayName("getDetailRoom - 열린 콜이면 방 요약을 돌려준다")
    @Test
    void getDetailRoom() {

        /*
         * settings
         */
        PartySummary party = party(null);

        doReturn(true).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));
        doReturn(Optional.of(party)).when(partyAccess).findSummary(eq(PARTY_ID));

        /*
         * test
         */
        PartySummary result = dispatchService.getDetailRoom(DRIVER_ID, PARTY_ID);

        /*
         * verify
         */
        assertThat(result).isEqualTo(party);
    }

    @DisplayName("getDetailRoom - 닫힌 콜이면 CALL_CLOSED 이고 방을 조회하지 않는다")
    @Test
    void getDetailRoom_closed() {

        /*
         * settings
         */
        doReturn(false).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> dispatchService.getDetailRoom(DRIVER_ID, PARTY_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.CALL_CLOSED);
        verifyNoInteractions(partyAccess);
    }

    @DisplayName("getDetailRoom - 열린 콜인데 방이 없으면 PARTY_NOT_FOUND")
    @Test
    void getDetailRoom_partyNotFound() {

        /*
         * settings
         */
        doReturn(true).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));
        doReturn(Optional.empty()).when(partyAccess).findSummary(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> dispatchService.getDetailRoom(DRIVER_ID, PARTY_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.PARTY_NOT_FOUND);
    }

    @DisplayName("isCallOpen - 후보에 있으면 true")
    @Test
    void isCallOpen() {

        /*
         * settings
         */
        doReturn(true).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        boolean open = dispatchService.isCallOpen(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        assertThat(open).isTrue();
    }

    @DisplayName("isCallOpen - 후보에 없으면 false")
    @Test
    void isCallOpen_notCandidate() {

        /*
         * settings
         */
        doReturn(false).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        boolean open = dispatchService.isCallOpen(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        assertThat(open).isFalse();
    }

    private void stubAcceptable() {
        doReturn(true).when(callCandidates).contains(eq(PARTY_ID), eq(DRIVER_ID));
        doReturn(true).when(driverAccess).lockAndCanReceiveCalls(eq(DRIVER_ID));
        doReturn(false).when(partyAccess).hasOngoingRide(eq(DRIVER_ID));
        doReturn(Optional.of(DRIVER_USER_ID)).when(driverAccess).findUserId(eq(DRIVER_ID));
        doReturn(false).when(partyAccess).hasMemberOnParty(eq(PARTY_ID), eq(DRIVER_USER_ID));
    }

    private static PartySummary party(Long driverId) {
        return new PartySummary(PARTY_ID, DEPARTURE_LATITUDE, DEPARTURE_LONGITUDE, 37.5665, 126.9780,
                "강남역", "서울시청", 3, 15000, 1800, driverId);
    }
}
