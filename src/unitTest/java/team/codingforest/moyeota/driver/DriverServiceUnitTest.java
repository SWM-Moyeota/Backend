package team.codingforest.moyeota.driver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.DriverSetting;
import team.codingforest.moyeota.driver.domain.DriverStatus;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.driver.domain.Vehicle;
import team.codingforest.moyeota.driver.dto.DriverResult;
import team.codingforest.moyeota.driver.dto.RegisterDriverCommand;
import team.codingforest.moyeota.driver.dto.RegisterVehicleCommand;
import team.codingforest.moyeota.driver.exception.DriverErrorCode;
import team.codingforest.moyeota.user.api.UserAccess;

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
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DriverServiceUnitTest {
    private static final Long DRIVER_ID = 9L;
    private static final Long USER_ID = 1L;
    private static final String NICKNAME = "모여타기사";
    private static final String QUALIFICATION_NUMBER = "서울-1234-5678";
    private static final BankAccount BANK_ACCOUNT = new BankAccount("국민은행", "123-456");
    private static final Vehicle VEHICLE = new Vehicle(4, "12가3456", "중형");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-01-01T00:00:00Z");

    @InjectMocks
    private DriverService driverService;
    @Mock
    private Drivers drivers;
    @Mock
    private UserAccess userAccess;

    @DisplayName("register - PENDING 상태로 차량까지 담아 저장하고 저장된 기사와 닉네임으로 결과를 만든다")
    @Test
    void register() {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, QUALIFICATION_NUMBER, "국민은행", "123-456", 4, "12가3456", "중형");
        Driver saved = driver(DriverStatus.PENDING, VEHICLE, true, null, null);
        ArgumentCaptor<Driver> captor = ArgumentCaptor.forClass(Driver.class);

        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));
        doReturn(saved).when(drivers).save(any(Driver.class));
        doReturn(Optional.of(NICKNAME)).when(userAccess).findNickname(eq(USER_ID));

        /*
         * test
         */
        DriverResult result = driverService.register(command);

        /*
         * verify
         */
        assertThat(result).isEqualTo(new DriverResult(DRIVER_ID, USER_ID, NICKNAME, DriverStatus.PENDING, true));

        verify(drivers).save(captor.capture());
        Driver created = captor.getValue();
        assertThat(created.getId()).isNull();
        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getQualificationNumber()).isEqualTo(QUALIFICATION_NUMBER);
        assertThat(created.getBankAccount()).isEqualTo(new BankAccount("국민은행", "123-456"));
        assertThat(created.getVehicle()).isEqualTo(new Vehicle(4, "12가3456", "중형"));
        assertThat(created.getStatus()).isEqualTo(DriverStatus.PENDING);
        assertThat(created.getSetting().isCallEnabled()).isTrue();
    }

    @DisplayName("register - 닉네임이 없으면 name 이 null 인 결과를 돌려준다")
    @Test
    void register_noNickname() {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, QUALIFICATION_NUMBER, "국민은행", "123-456", 4, "12가3456", "중형");

        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));
        doReturn(driver(DriverStatus.PENDING, VEHICLE, true, null, null)).when(drivers).save(any(Driver.class));
        doReturn(Optional.empty()).when(userAccess).findNickname(eq(USER_ID));

        /*
         * test
         */
        DriverResult result = driverService.register(command);

        /*
         * verify
         */
        assertThat(result.name()).isNull();
        assertThat(result.id()).isEqualTo(DRIVER_ID);
    }

    @DisplayName("register - 이미 기사면 DRIVER_ALREADY_REGISTERED 이고 저장하지 않는다")
    @Test
    void register_alreadyRegistered() {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, QUALIFICATION_NUMBER, "국민은행", "123-456", 4, "12가3456", "중형");

        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, true, null, null))).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.register(command));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_ALREADY_REGISTERED);
        verify(drivers, never()).save(any());
        verifyNoInteractions(userAccess);
    }

    @DisplayName("register - 은행명이 비어 있으면 INVALID_BANK_NAME 이고 저장하지 않는다")
    @Test
    void register_blankBankName() {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, QUALIFICATION_NUMBER, " ", "123-456", 4, "12가3456", "중형");

        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.register(command));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.INVALID_BANK_NAME);
        verify(drivers, never()).save(any());
    }

    @DisplayName("register - 은행명이 null 이면 INVALID_BANK_NAME 이고 저장하지 않는다")
    @Test
    void register_nullBankName() {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, QUALIFICATION_NUMBER, null, "123-456", 4, "12가3456", "중형");

        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.register(command));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.INVALID_BANK_NAME);
        verify(drivers, never()).save(any());
    }

    @DisplayName("register - 계좌번호가 비어 있으면 INVALID_ACCOUNT_NUMBER 이고 저장하지 않는다")
    @Test
    void register_blankAccountNumber() {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, QUALIFICATION_NUMBER, "국민은행", " ", 4, "12가3456", "중형");

        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.register(command));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.INVALID_ACCOUNT_NUMBER);
        verify(drivers, never()).save(any());
    }

    @DisplayName("register - 차량 번호가 비어 있으면 INVALID_PLATE_NUMBER 이고 저장하지 않는다")
    @Test
    void register_blankPlateNumber() {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, QUALIFICATION_NUMBER, "국민은행", "123-456", 4, " ", "중형");

        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.register(command));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.INVALID_PLATE_NUMBER);
        verify(drivers, never()).save(any());
    }

    @DisplayName("register - 좌석 수가 null 이면 INVALID_VEHICLE_SEATS 이고 저장하지 않는다")
    @Test
    void register_nullSeats() {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, QUALIFICATION_NUMBER, "국민은행", "123-456", null, "12가3456", "중형");

        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.register(command));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.INVALID_VEHICLE_SEATS);
        verify(drivers, never()).save(any());
    }

    @DisplayName("register - 좌석 수가 0 이면 INVALID_VEHICLE_SEATS 이고 저장하지 않는다")
    @Test
    void register_zeroSeats() {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, QUALIFICATION_NUMBER, "국민은행", "123-456", 0, "12가3456", "중형");

        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.register(command));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.INVALID_VEHICLE_SEATS);
        verify(drivers, never()).save(any());
    }

    @DisplayName("register - 저장이 실패하면 예외를 그대로 던지고 닉네임을 조회하지 않는다")
    @Test
    void register_saveFailed() {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, QUALIFICATION_NUMBER, "국민은행", "123-456", 4, "12가3456", "중형");

        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));
        doThrow(new IllegalStateException("db down")).when(drivers).save(any(Driver.class));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.register(command));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("db down");
        verifyNoInteractions(userAccess);
    }

    @DisplayName("verify - PENDING 기사를 잠금 조회해 VERIFIED 로 바꾸고 현재 시각을 넣어 저장한다")
    @Test
    void verify_success() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.PENDING, VEHICLE, true, null, null);
        Instant before = Instant.now();

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        driverService.verify(DRIVER_ID);

        /*
         * verify
         */
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.VERIFIED);
        assertThat(driver.getVerifiedAt()).isBetween(before, Instant.now());
        verify(drivers).save(eq(driver));
        verify(drivers, never()).findById(any());
    }

    @DisplayName("verify - 이미 VERIFIED 면 DRIVER_NOT_PENDING 이고 저장하지 않는다")
    @Test
    void verify_alreadyVerified() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, true, null, null))).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.verify(DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_PENDING);
        verify(drivers, never()).save(any());
    }

    @DisplayName("verify - 기사가 없으면 DRIVER_NOT_FOUND")
    @Test
    void verify_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.verify(DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        verify(drivers, never()).save(any());
    }

    @DisplayName("verify - 탈퇴한 기사면 DRIVER_NOT_FOUND 이고 상태를 바꾸지 않는다")
    @Test
    void verify_withdrawn() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.PENDING, VEHICLE, true, null, WITHDRAWN_AT);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.verify(DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.PENDING);
        verify(drivers, never()).save(any());
    }

    @DisplayName("registerVehicle - 차량이 없던 기사에 차량을 넣어 저장한다")
    @Test
    void registerVehicle() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, null, true, null, null);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        driverService.registerVehicle(new RegisterVehicleCommand(DRIVER_ID, 4, "34나5678", "대형"));

        /*
         * verify
         */
        assertThat(driver.getVehicle()).isEqualTo(new Vehicle(4, "34나5678", "대형"));
        verify(drivers).save(eq(driver));
    }

    @DisplayName("registerVehicle - 기존 차량이 있으면 새 차량으로 교체한다")
    @Test
    void registerVehicle_replace() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, true, null, null);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        driverService.registerVehicle(new RegisterVehicleCommand(DRIVER_ID, 6, "34나5678", "대형"));

        /*
         * verify
         */
        assertThat(driver.getVehicle()).isEqualTo(new Vehicle(6, "34나5678", "대형"));
        verify(drivers).save(eq(driver));
    }

    @DisplayName("registerVehicle - 기사가 없으면 DRIVER_NOT_FOUND")
    @Test
    void registerVehicle_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.registerVehicle(new RegisterVehicleCommand(DRIVER_ID, 4, "34나5678", "대형")));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        verify(drivers, never()).save(any());
    }

    @DisplayName("registerVehicle - 탈퇴한 기사면 DRIVER_NOT_FOUND 이고 차량을 바꾸지 않는다")
    @Test
    void registerVehicle_withdrawn() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, true, null, WITHDRAWN_AT);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.registerVehicle(new RegisterVehicleCommand(DRIVER_ID, 6, "34나5678", "대형")));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        assertThat(driver.getVehicle()).isEqualTo(VEHICLE);
        verify(drivers, never()).save(any());
    }

    @DisplayName("registerVehicle - 차량 번호가 비어 있으면 INVALID_PLATE_NUMBER 이고 기존 차량을 유지한다")
    @Test
    void registerVehicle_blankPlateNumber() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, true, null, null);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.registerVehicle(new RegisterVehicleCommand(DRIVER_ID, 4, " ", "대형")));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.INVALID_PLATE_NUMBER);
        assertThat(driver.getVehicle()).isEqualTo(VEHICLE);
        verify(drivers, never()).save(any());
    }

    @DisplayName("registerVehicle - 좌석 수가 null 이면 INVALID_VEHICLE_SEATS 이고 기존 차량을 유지한다")
    @Test
    void registerVehicle_nullSeats() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, true, null, null);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.registerVehicle(new RegisterVehicleCommand(DRIVER_ID, null, "34나5678", "대형")));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.INVALID_VEHICLE_SEATS);
        assertThat(driver.getVehicle()).isEqualTo(VEHICLE);
        verify(drivers, never()).save(any());
    }

    @DisplayName("enableCall - 꺼져 있던 콜 수신을 켜고 저장한다")
    @Test
    void enableCall() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, false, null, null);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        driverService.enableCall(DRIVER_ID);

        /*
         * verify
         */
        assertThat(driver.getSetting().isCallEnabled()).isTrue();
        verify(drivers).save(eq(driver));
    }

    @DisplayName("enableCall - 기사가 없으면 DRIVER_NOT_FOUND")
    @Test
    void enableCall_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.enableCall(DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        verify(drivers, never()).save(any());
    }

    @DisplayName("enableCall - 탈퇴한 기사면 DRIVER_NOT_FOUND 이고 콜을 켜지 않는다")
    @Test
    void enableCall_withdrawn() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, false, null, WITHDRAWN_AT);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.enableCall(DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        assertThat(driver.getSetting().isCallEnabled()).isFalse();
        verify(drivers, never()).save(any());
    }

    @DisplayName("disableCall - 켜져 있던 콜 수신을 끄고 저장한다")
    @Test
    void disableCall() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, true, null, null);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        driverService.disableCall(DRIVER_ID);

        /*
         * verify
         */
        assertThat(driver.getSetting().isCallEnabled()).isFalse();
        verify(drivers).save(eq(driver));
    }

    @DisplayName("disableCall - 기사가 없으면 DRIVER_NOT_FOUND")
    @Test
    void disableCall_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.disableCall(DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        verify(drivers, never()).save(any());
    }

    @DisplayName("disableCall - 탈퇴한 기사면 DRIVER_NOT_FOUND 이고 저장하지 않는다")
    @Test
    void disableCall_withdrawn() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, true, null, WITHDRAWN_AT))).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.disableCall(DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        verify(drivers, never()).save(any());
    }

    @DisplayName("registerFcmToken - 토큰을 넣어 저장한다")
    @Test
    void registerFcmToken() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, true, null, null);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        driverService.registerFcmToken(DRIVER_ID, "new-token");

        /*
         * verify
         */
        assertThat(driver.getFcmToken()).isEqualTo("new-token");
        verify(drivers).save(eq(driver));
    }

    @DisplayName("registerFcmToken - 기존 토큰이 있으면 새 토큰으로 덮어쓴다")
    @Test
    void registerFcmToken_overwrite() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, true, "old-token", null);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        driverService.registerFcmToken(DRIVER_ID, "new-token");

        /*
         * verify
         */
        assertThat(driver.getFcmToken()).isEqualTo("new-token");
        verify(drivers).save(eq(driver));
    }

    @DisplayName("registerFcmToken - 토큰이 null 이면 DRIVER_EMPTY_FCM_TOKEN 이고 기존 토큰을 유지한다")
    @Test
    void registerFcmToken_null() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, true, "old-token", null);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.registerFcmToken(DRIVER_ID, null));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_EMPTY_FCM_TOKEN);
        assertThat(driver.getFcmToken()).isEqualTo("old-token");
        verify(drivers, never()).save(any());
    }

    @DisplayName("registerFcmToken - 토큰이 빈 문자열이면 DRIVER_EMPTY_FCM_TOKEN 이고 저장하지 않는다")
    @Test
    void registerFcmToken_empty() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, true, null, null))).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.registerFcmToken(DRIVER_ID, ""));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_EMPTY_FCM_TOKEN);
        verify(drivers, never()).save(any());
    }

    @DisplayName("registerFcmToken - 토큰이 공백뿐이면 DRIVER_EMPTY_FCM_TOKEN 이고 저장하지 않는다")
    @Test
    void registerFcmToken_blank() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, true, null, null))).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.registerFcmToken(DRIVER_ID, "   "));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_EMPTY_FCM_TOKEN);
        verify(drivers, never()).save(any());
    }

    @DisplayName("registerFcmToken - 기사가 없으면 DRIVER_NOT_FOUND")
    @Test
    void registerFcmToken_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.registerFcmToken(DRIVER_ID, "new-token"));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        verify(drivers, never()).save(any());
    }

    @DisplayName("registerFcmToken - 탈퇴한 기사면 DRIVER_NOT_FOUND 이고 토큰을 넣지 않는다")
    @Test
    void registerFcmToken_withdrawn() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, false, null, WITHDRAWN_AT);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.registerFcmToken(DRIVER_ID, "new-token"));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        assertThat(driver.getFcmToken()).isNull();
        verify(drivers, never()).save(any());
    }

    @DisplayName("removeFcmToken - 토큰을 지우고 저장한다")
    @Test
    void removeFcmToken() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, true, "old-token", null);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        driverService.removeFcmToken(DRIVER_ID);

        /*
         * verify
         */
        assertThat(driver.getFcmToken()).isNull();
        verify(drivers).save(eq(driver));
    }

    @DisplayName("removeFcmToken - 토큰이 원래 없어도 실패하지 않는다")
    @Test
    void removeFcmToken_noToken() {

        /*
         * settings
         */
        Driver driver = driver(DriverStatus.VERIFIED, VEHICLE, true, null, null);

        doReturn(Optional.of(driver)).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        driverService.removeFcmToken(DRIVER_ID);

        /*
         * verify
         */
        assertThat(driver.getFcmToken()).isNull();
        verify(drivers).save(eq(driver));
    }

    @DisplayName("removeFcmToken - 기사가 없으면 DRIVER_NOT_FOUND")
    @Test
    void removeFcmToken_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.removeFcmToken(DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        verify(drivers, never()).save(any());
    }

    @DisplayName("removeFcmToken - 탈퇴한 기사면 DRIVER_NOT_FOUND 이고 저장하지 않는다")
    @Test
    void removeFcmToken_withdrawn() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, false, null, WITHDRAWN_AT))).when(drivers).findByIdForUpdate(eq(DRIVER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.removeFcmToken(DRIVER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_FOUND);
        verify(drivers, never()).save(any());
    }

    @DisplayName("getByUserId - 기사 정보와 닉네임으로 결과를 만든다")
    @Test
    void getByUserId() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, false, null, null))).when(drivers).findByUserId(eq(USER_ID));
        doReturn(Optional.of(NICKNAME)).when(userAccess).findNickname(eq(USER_ID));

        /*
         * test
         */
        DriverResult result = driverService.getByUserId(USER_ID);

        /*
         * verify
         */
        assertThat(result).isEqualTo(new DriverResult(DRIVER_ID, USER_ID, NICKNAME, DriverStatus.VERIFIED, false));
    }

    @DisplayName("getByUserId - 닉네임이 없으면 name 이 null")
    @Test
    void getByUserId_noNickname() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.PENDING, VEHICLE, true, null, null))).when(drivers).findByUserId(eq(USER_ID));
        doReturn(Optional.empty()).when(userAccess).findNickname(eq(USER_ID));

        /*
         * test
         */
        DriverResult result = driverService.getByUserId(USER_ID);

        /*
         * verify
         */
        assertThat(result).isEqualTo(new DriverResult(DRIVER_ID, USER_ID, null, DriverStatus.PENDING, true));
    }

    @DisplayName("getByUserId - 기사가 아니면 DRIVER_NOT_REGISTERED")
    @Test
    void getByUserId_notRegistered() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.getByUserId(USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_REGISTERED);
        verifyNoInteractions(userAccess);
    }

    @DisplayName("getByUserId - 탈퇴한 기사면 DRIVER_NOT_REGISTERED 이고 닉네임을 조회하지 않는다")
    @Test
    void getByUserId_withdrawn() {

        /*
         * settings
         */
        doReturn(Optional.of(driver(DriverStatus.VERIFIED, VEHICLE, false, null, WITHDRAWN_AT))).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> driverService.getByUserId(USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DriverErrorCode.DRIVER_NOT_REGISTERED);
        verifyNoInteractions(userAccess);
    }

    private static Driver driver(DriverStatus status, Vehicle vehicle, boolean callEnabled, String fcmToken, Instant deletedAt) {
        return Driver.load(DRIVER_ID, USER_ID, QUALIFICATION_NUMBER, null, BANK_ACCOUNT, vehicle,
                DriverSetting.load(callEnabled), status, fcmToken, deletedAt);
    }
}
