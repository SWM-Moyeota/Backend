package team.codingforest.moyeota.driver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.driver.api.DriverSummary;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.DriverSetting;
import team.codingforest.moyeota.driver.domain.DriverStatus;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.driver.domain.Vehicle;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DriverAccessServiceUnitTest {
    private static final Long DRIVER_ID = 9L;
    private static final Long USER_ID = 1L;
    private static final Vehicle VEHICLE = new Vehicle(4, "12가3456", "중형");

    @InjectMocks
    private DriverAccessService driverAccessService;
    @Mock
    private Drivers drivers;

    @DisplayName("canReceiveCalls - 검증·차량·콜 수신이 모두 갖춰지면 잠금 없이 조회해 true")
    @Test
    void canReceiveCalls() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, true, null))).when(drivers).findById(eq(DRIVER_ID));

        /*
         * test
         */
        boolean result = driverAccessService.canReceiveCalls(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isTrue();
        verify(drivers, never()).findByIdForUpdate(any());
    }

    @DisplayName("canReceiveCalls - 기사가 없으면 false")
    @Test
    void canReceiveCalls_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findById(eq(DRIVER_ID));

        /*
         * test
         */
        boolean result = driverAccessService.canReceiveCalls(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("canReceiveCalls - 검증 전이면 false")
    @Test
    void canReceiveCalls_pending() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.PENDING, VEHICLE, true, null))).when(drivers).findById(eq(DRIVER_ID));

        /*
         * test
         */
        boolean result = driverAccessService.canReceiveCalls(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("canReceiveCalls - 차량이 없으면 false")
    @Test
    void canReceiveCalls_noVehicle() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, null, true, null))).when(drivers).findById(eq(DRIVER_ID));

        /*
         * test
         */
        boolean result = driverAccessService.canReceiveCalls(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("canReceiveCalls - 콜 수신을 껐으면 false")
    @Test
    void canReceiveCalls_callDisabled() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, false, null))).when(drivers).findById(eq(DRIVER_ID));

        /*
         * test
         */
        boolean result = driverAccessService.canReceiveCalls(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("canReceiveCalls - 탈퇴한 기사면 다른 조건이 맞아도 false")
    @Test
    void canReceiveCalls_withdrawn() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, true, Instant.now()))).when(drivers).findById(eq(DRIVER_ID));

        /*
         * test
         */
        boolean result = driverAccessService.canReceiveCalls(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("findFcmTokens - 저장소 결과를 그대로 돌려준다")
    @Test
    void findFcmTokens() {

        /*
         * settings
         */
        List<Long> driverIds = List.of(DRIVER_ID, 10L);
        Map<Long, String> tokens = Map.of(DRIVER_ID, "token-9");

        doReturn(tokens).when(drivers).findFcmTokens(eq(driverIds));

        /*
         * test
         */
        Map<Long, String> result = driverAccessService.findFcmTokens(driverIds);

        /*
         * verify
         */
        assertThat(result).isEqualTo(tokens);
    }

    @DisplayName("findFcmTokens - 빈 목록이면 빈 결과")
    @Test
    void findFcmTokens_empty() {

        /*
         * settings
         */
        doReturn(Map.of()).when(drivers).findFcmTokens(eq(List.of()));

        /*
         * test
         */
        Map<Long, String> result = driverAccessService.findFcmTokens(List.of());

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    @DisplayName("findUserId - 기사의 유저 번호를 돌려준다")
    @Test
    void findUserId() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, true, null))).when(drivers).findById(eq(DRIVER_ID));

        /*
         * test
         */
        Optional<Long> result = driverAccessService.findUserId(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).contains(USER_ID);
    }

    @DisplayName("findUserId - 기사가 없으면 빈 값")
    @Test
    void findUserId_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findById(eq(DRIVER_ID));

        /*
         * test
         */
        Optional<Long> result = driverAccessService.findUserId(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    @DisplayName("lockIdByUserId - 잠금 조회로 기사 번호를 돌려준다")
    @Test
    void lockIdByUserId() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, true, null))).when(drivers).findByUserIdForUpdate(eq(USER_ID));

        /*
         * test
         */
        Optional<Long> result = driverAccessService.lockIdByUserId(USER_ID);

        /*
         * verify
         */
        assertThat(result).contains(DRIVER_ID);
        verify(drivers, never()).findByUserId(any());
    }

    @DisplayName("lockIdByUserId - 기사가 아니면 빈 값")
    @Test
    void lockIdByUserId_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserIdForUpdate(eq(USER_ID));

        /*
         * test
         */
        Optional<Long> result = driverAccessService.lockIdByUserId(USER_ID);

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    @DisplayName("lockAndCanReceiveCalls - 잠금 조회 결과로 판단해 true")
    @Test
    void lockAndCanReceiveCalls() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, true, null))).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        boolean result = driverAccessService.lockAndCanReceiveCalls(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isTrue();
        verify(drivers, never()).findById(any());
    }

    @DisplayName("lockAndCanReceiveCalls - 기사가 없으면 false")
    @Test
    void lockAndCanReceiveCalls_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        boolean result = driverAccessService.lockAndCanReceiveCalls(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("lockAndCanReceiveCalls - 잠금 뒤 다시 본 상태가 콜 불가면 false")
    @Test
    void lockAndCanReceiveCalls_callDisabled() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, false, null))).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        boolean result = driverAccessService.lockAndCanReceiveCalls(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("findSummary - 차량 정보를 요약으로 돌려준다")
    @Test
    void findSummary() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, true, null))).when(drivers).findById(eq(DRIVER_ID));

        /*
         * test
         */
        Optional<DriverSummary> result = driverAccessService.findSummary(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).contains(new DriverSummary(4, "12가3456", "중형"));
    }

    @DisplayName("findSummary - 차량이 없으면 빈 값")
    @Test
    void findSummary_noVehicle() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, null, true, null))).when(drivers).findById(eq(DRIVER_ID));

        /*
         * test
         */
        Optional<DriverSummary> result = driverAccessService.findSummary(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    @DisplayName("findSummary - 기사가 없으면 빈 값")
    @Test
    void findSummary_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findById(eq(DRIVER_ID));

        /*
         * test
         */
        Optional<DriverSummary> result = driverAccessService.findSummary(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    private static Driver driver(DriverStatus status, Vehicle vehicle, boolean callEnabled, Instant deletedAt) {
        return Driver.load(DRIVER_ID, USER_ID, "서울-1234-5678", null, new BankAccount("국민은행", "123-456"),
                vehicle, DriverSetting.load(callEnabled), status, "token", deletedAt);
    }
}
