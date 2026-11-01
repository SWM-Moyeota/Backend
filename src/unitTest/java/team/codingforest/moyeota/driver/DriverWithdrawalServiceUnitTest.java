package team.codingforest.moyeota.driver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.DriverSetting;
import team.codingforest.moyeota.driver.domain.DriverStatus;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.driver.domain.Vehicle;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DriverWithdrawalServiceUnitTest {
    private static final Long USER_ID = 1L;
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-10-31T00:00:00Z");

    @InjectMocks
    private DriverWithdrawalService driverWithdrawalService;
    @Mock
    private Drivers drivers;

    @DisplayName("withdraw - 잠금 조회한 기사를 탈퇴 처리해 계좌·토큰을 지우고 콜을 끈 뒤 저장한다")
    @Test
    void withdraw() {

        /*
         * settings
         */
        Driver driver = driver(null);

        doReturn(Optional.of(driver)).when(drivers).findByUserIdForUpdate(eq(USER_ID));

        /*
         * test
         */
        driverWithdrawalService.withdraw(USER_ID, WITHDRAWN_AT);

        /*
         * verify
         */
        assertThat(driver.getDeletedAt()).isEqualTo(WITHDRAWN_AT);
        assertThat(driver.getBankAccount()).isNull();
        assertThat(driver.getFcmToken()).isNull();
        assertThat(driver.getSetting().isCallEnabled()).isFalse();
        verify(drivers).save(eq(driver));
        verify(drivers, never()).findByUserId(any());
    }

    @DisplayName("withdraw - 기사가 아닌 유저면 아무것도 저장하지 않는다")
    @Test
    void withdraw_notDriver() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserIdForUpdate(eq(USER_ID));

        /*
         * test
         */
        driverWithdrawalService.withdraw(USER_ID, WITHDRAWN_AT);

        /*
         * verify
         */
        verify(drivers, never()).save(any());
    }

    @DisplayName("withdraw - 이미 탈퇴한 기사면 처음 탈퇴 시각을 덮어쓰지 않고 저장도 하지 않는다")
    @Test
    void withdraw_alreadyWithdrawn() {

        /*
         * settings
         */
        Instant firstWithdrawnAt = Instant.parse("2026-01-01T00:00:00Z");
        Driver driver = driver(firstWithdrawnAt);

        doReturn(Optional.of(driver)).when(drivers).findByUserIdForUpdate(eq(USER_ID));

        /*
         * test
         */
        driverWithdrawalService.withdraw(USER_ID, WITHDRAWN_AT);

        /*
         * verify
         */
        assertThat(driver.getDeletedAt()).isEqualTo(firstWithdrawnAt);
        verify(drivers, never()).save(any());
    }

    @DisplayName("withdraw - 저장이 실패하면 예외를 그대로 던진다 (유저 탈퇴 트랜잭션 롤백 대상)")
    @Test
    void withdraw_saveFailed() {

        /*
         * settings
         */
        Driver driver = driver(null);

        doReturn(Optional.of(driver)).when(drivers).findByUserIdForUpdate(eq(USER_ID));
        doThrow(new IllegalStateException("db down")).when(drivers).save(eq(driver));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverWithdrawalService.withdraw(USER_ID, WITHDRAWN_AT));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("db down");
    }

    private static Driver driver(Instant deletedAt) {
        return Driver.load(9L, USER_ID, "서울-1234-5678", Instant.parse("2025-01-01T00:00:00Z"), new BankAccount("국민은행", "123-456"),
                new Vehicle(4, "12가3456", "중형"), DriverSetting.load(true), DriverStatus.VERIFIED, "token", deletedAt);
    }
}
