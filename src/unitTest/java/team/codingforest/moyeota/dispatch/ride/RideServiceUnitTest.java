package team.codingforest.moyeota.dispatch.ride;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.dispatch.exception.DispatchErrorCode;
import team.codingforest.moyeota.dispatch.location.domain.DriverLocations;
import team.codingforest.moyeota.dispatch.location.domain.DriverPosition;
import team.codingforest.moyeota.dispatch.ride.domain.PassengerNotifier;
import team.codingforest.moyeota.dispatch.ride.dto.DriverLocationResponse;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartySummary;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.settlement.api.SettlementAccess;
import team.codingforest.moyeota.settlement.api.dto.RideSettlementCommand;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RideServiceUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long DRIVER_ID = 9L;
    private static final Long MEMBER_ID = 1L;
    private static final int FARE = 12000;

    @InjectMocks
    private RideService rideService;
    @Mock
    private PartyAccess partyAccess;
    @Mock
    private PassengerNotifier passengerNotifier;
    @Mock
    private DriverLocations driverLocations;
    @Mock
    private SettlementAccess settlementAccess;

    @DisplayName("arrive - 픽업 대기 중이면 승객에게 도착을 알린다")
    @Test
    void arrive() {

        /*
         * settings
         */
        doReturn(true).when(partyAccess).isAwaitingPickup(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        rideService.arrive(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        verify(passengerNotifier).notifyDriverArrived(eq(PARTY_ID));
    }

    @DisplayName("arrive - 픽업 대기 중이 아니면 NOT_AWAITING_PICKUP 이고 알리지 않는다")
    @Test
    void arrive_notAwaitingPickup() {

        /*
         * settings
         */
        doReturn(false).when(partyAccess).isAwaitingPickup(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideService.arrive(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.NOT_AWAITING_PICKUP);
        verifyNoInteractions(passengerNotifier);
    }

    @DisplayName("arrive - 알림이 실패하면 예외를 그대로 던진다")
    @Test
    void arrive_notifyFailed() {

        /*
         * settings
         */
        doReturn(true).when(partyAccess).isAwaitingPickup(eq(PARTY_ID), eq(DRIVER_ID));
        doThrow(new IllegalStateException("fcm down")).when(passengerNotifier).notifyDriverArrived(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideService.arrive(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("fcm down");
    }

    @DisplayName("board - 운행을 시작한다")
    @Test
    void board() {

        /*
         * test
         */
        rideService.board(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        verify(partyAccess).startRide(eq(PARTY_ID), eq(DRIVER_ID));
    }

    @DisplayName("board - 배정 기사가 아니면 예외를 그대로 던진다")
    @Test
    void board_notAssignedDriver() {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.NOT_ASSIGNED_DRIVER)).when(partyAccess).startRide(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideService.board(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MatchingErrorCode.NOT_ASSIGNED_DRIVER);
    }

    @DisplayName("complete - 운행을 끝낸 뒤 프로모션 0 으로 정산을 기록한다")
    @Test
    void complete() {

        /*
         * settings
         */
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ArgumentCaptor<RideSettlementCommand> captor = ArgumentCaptor.forClass(RideSettlementCommand.class);

        /*
         * test
         */
        rideService.complete(PARTY_ID, DRIVER_ID, FARE);

        /*
         * verify
         */
        InOrder order = inOrder(partyAccess, settlementAccess);
        order.verify(partyAccess).completeRide(eq(PARTY_ID), eq(DRIVER_ID), eq(FARE));
        order.verify(settlementAccess).record(captor.capture());

        RideSettlementCommand command = captor.getValue();
        assertThat(command.partyId()).isEqualTo(PARTY_ID);
        assertThat(command.taxiDriverId()).isEqualTo(DRIVER_ID);
        assertThat(command.totalFare()).isEqualTo(FARE);
        assertThat(command.platformPromotion()).isZero();
        assertThat(command.occurredAt()).isBetween(before, Instant.now());
    }

    @DisplayName("complete - 정산 시각은 DB 정밀도에 맞춰 마이크로초로 자른다")
    @Test
    void complete_occurredAtTruncated() {

        /*
         * settings
         */
        ArgumentCaptor<RideSettlementCommand> captor = ArgumentCaptor.forClass(RideSettlementCommand.class);

        /*
         * test
         */
        rideService.complete(PARTY_ID, DRIVER_ID, FARE);

        /*
         * verify
         */
        verify(settlementAccess).record(captor.capture());
        assertThat(captor.getValue().occurredAt().getNano() % 1_000).isZero();
    }

    @DisplayName("complete - 운행 종료가 실패하면 예외를 그대로 던지고 정산을 기록하지 않는다")
    @Test
    void complete_notRiding() {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.NOT_RIDING)).when(partyAccess).completeRide(eq(PARTY_ID), eq(DRIVER_ID), eq(FARE));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideService.complete(PARTY_ID, DRIVER_ID, FARE));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MatchingErrorCode.NOT_RIDING);
        verifyNoInteractions(settlementAccess);
    }

    @DisplayName("complete - 정산 기록이 실패하면 예외를 그대로 던진다 (트랜잭션 롤백 대상)")
    @Test
    void complete_settlementFailed() {

        /*
         * settings
         */
        doThrow(new IllegalStateException("settlement down")).when(settlementAccess).record(any(RideSettlementCommand.class));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideService.complete(PARTY_ID, DRIVER_ID, FARE));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("settlement down");
    }

    @DisplayName("driverLocation - 위치의 경도·위도를 필드에 맞게 담아 돌려준다")
    @Test
    void driverLocation() {

        /*
         * settings
         */
        doReturn(true).when(partyAccess).hasMemberOnParty(eq(PARTY_ID), eq(MEMBER_ID));
        doReturn(Optional.of(summary(DRIVER_ID))).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(Optional.of(new DriverPosition(37.4979, 127.0276))).when(driverLocations).getLocationDriver(eq(DRIVER_ID));

        /*
         * test
         */
        DriverLocationResponse response = rideService.driverLocation(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(response.latitude()).isEqualTo(37.4979);
        assertThat(response.longitude()).isEqualTo(127.0276);
    }

    @DisplayName("driverLocation - 방 구성원이 아니면 NOT_PARTY_MEMBER 이고 방을 조회하지 않는다")
    @Test
    void driverLocation_notMember() {

        /*
         * settings
         */
        doReturn(false).when(partyAccess).hasMemberOnParty(eq(PARTY_ID), eq(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideService.driverLocation(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.NOT_PARTY_MEMBER);
        verify(partyAccess, never()).findSummary(any());
        verifyNoInteractions(driverLocations);
    }

    @DisplayName("driverLocation - 방이 없으면 PARTY_NOT_FOUND")
    @Test
    void driverLocation_partyNotFound() {

        /*
         * settings
         */
        doReturn(true).when(partyAccess).hasMemberOnParty(eq(PARTY_ID), eq(MEMBER_ID));
        doReturn(Optional.empty()).when(partyAccess).findSummary(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideService.driverLocation(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(driverLocations);
    }

    @DisplayName("driverLocation - 기사가 배정 전이면 DRIVER_NOT_ASSIGNED 이고 위치를 조회하지 않는다")
    @Test
    void driverLocation_driverNotAssigned() {

        /*
         * settings
         */
        doReturn(true).when(partyAccess).hasMemberOnParty(eq(PARTY_ID), eq(MEMBER_ID));
        doReturn(Optional.of(summary(null))).when(partyAccess).findSummary(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideService.driverLocation(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.DRIVER_NOT_ASSIGNED);
        verifyNoInteractions(driverLocations);
    }

    @DisplayName("driverLocation - 기사 위치가 없으면 DRIVER_LOCATION_UNAVAILABLE")
    @Test
    void driverLocation_locationUnavailable() {

        /*
         * settings
         */
        doReturn(true).when(partyAccess).hasMemberOnParty(eq(PARTY_ID), eq(MEMBER_ID));
        doReturn(Optional.of(summary(DRIVER_ID))).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(Optional.empty()).when(driverLocations).getLocationDriver(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideService.driverLocation(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.DRIVER_LOCATION_UNAVAILABLE);
    }

    private static PartySummary summary(Long driverId) {
        return new PartySummary(PARTY_ID, 37.4979, 127.0276, 37.5665, 126.9780, "강남역", "서울시청", 3, 15000, 1800, driverId);
    }
}
