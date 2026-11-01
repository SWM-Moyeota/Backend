package team.codingforest.moyeota.dispatch.location;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.dispatch.exception.DispatchErrorCode;
import team.codingforest.moyeota.dispatch.location.domain.DriverLocations;
import team.codingforest.moyeota.driver.api.DriverAccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DriverLocationServiceUnitTest {
    private static final Long DRIVER_ID = 9L;
    private static final double LATITUDE = 37.4979;
    private static final double LONGITUDE = 127.0276;

    @InjectMocks
    private DriverLocationService driverLocationService;
    @Mock
    private DriverLocations driverLocations;
    @Mock
    private DriverAccess driverAccess;

    @DisplayName("goOnline - 콜을 받을 수 있으면 위치를 위도·경도 순서로 등록한다")
    @Test
    void goOnline() {

        /*
         * settings
         */
        doReturn(true).when(driverAccess).canReceiveCalls(eq(DRIVER_ID));

        /*
         * test
         */
        driverLocationService.goOnline(DRIVER_ID, LATITUDE, LONGITUDE);

        /*
         * verify
         */
        verify(driverLocations).update(eq(DRIVER_ID), eq(LATITUDE), eq(LONGITUDE));
    }

    @DisplayName("goOnline - 콜을 받을 수 없으면 DRIVER_CANNOT_RECEIVE 이고 위치를 등록하지 않는다")
    @Test
    void goOnline_cannotReceive() {

        /*
         * settings
         */
        doReturn(false).when(driverAccess).canReceiveCalls(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverLocationService.goOnline(DRIVER_ID, LATITUDE, LONGITUDE));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DispatchErrorCode.DRIVER_CANNOT_RECEIVE);
        verifyNoInteractions(driverLocations);
    }

    @DisplayName("goOnline - 위치 저장이 실패하면 예외를 그대로 던진다")
    @Test
    void goOnline_updateFailed() {

        /*
         * settings
         */
        doReturn(true).when(driverAccess).canReceiveCalls(eq(DRIVER_ID));
        doThrow(new IllegalStateException("redis down")).when(driverLocations).update(eq(DRIVER_ID), eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverLocationService.goOnline(DRIVER_ID, LATITUDE, LONGITUDE));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("redis down");
    }

    @DisplayName("report - 자격을 다시 확인하지 않고 위치를 위도·경도 순서로 갱신한다")
    @Test
    void report() {

        /*
         * test
         */
        driverLocationService.report(DRIVER_ID, LATITUDE, LONGITUDE);

        /*
         * verify
         */
        verify(driverLocations).update(eq(DRIVER_ID), eq(LATITUDE), eq(LONGITUDE));
        verifyNoInteractions(driverAccess);
    }

    @DisplayName("report - 위치 저장이 실패하면 예외를 그대로 던진다")
    @Test
    void report_updateFailed() {

        /*
         * settings
         */
        doThrow(new IllegalStateException("redis down")).when(driverLocations).update(eq(DRIVER_ID), eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverLocationService.report(DRIVER_ID, LATITUDE, LONGITUDE));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("redis down");
    }

    @DisplayName("goOffline - 자격 확인 없이 위치를 지운다")
    @Test
    void goOffline() {

        /*
         * test
         */
        driverLocationService.goOffline(DRIVER_ID);

        /*
         * verify
         */
        verify(driverLocations).remove(eq(DRIVER_ID));
        verifyNoInteractions(driverAccess);
    }

    @DisplayName("goOffline - 위치 삭제가 실패하면 예외를 그대로 던진다")
    @Test
    void goOffline_removeFailed() {

        /*
         * settings
         */
        doThrow(new IllegalStateException("redis down")).when(driverLocations).remove(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverLocationService.goOffline(DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("redis down");
    }
}
