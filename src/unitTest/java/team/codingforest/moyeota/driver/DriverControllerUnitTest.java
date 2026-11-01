package team.codingforest.moyeota.driver;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.DriverSetting;
import team.codingforest.moyeota.driver.domain.DriverStatus;
import team.codingforest.moyeota.driver.dto.DriverResult;
import team.codingforest.moyeota.driver.dto.RegisterDriverCommand;
import team.codingforest.moyeota.driver.dto.RegisterVehicleCommand;
import team.codingforest.moyeota.driver.exception.DriverErrorCode;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DriverController.class)
class DriverControllerUnitTest extends ControllerUnitTestSupport {
    private static final String VALID_VEHICLE = """
            {
                "seats": 4,
                "plateNumber": "12가3456",
                "type": "중형"
            }
            """;
    private static final String VALID_REGISTER = """
            {
                "qualificationNumber": "서울-1234-5678",
                "bankName": "국민은행",
                "accountNumber": "123-456-789012",
                "vehicle": {
                    "seats": 4,
                    "plateNumber": "12가3456",
                    "type": "중형"
                }
            }
            """;
    private static final String VALID_FCM_TOKEN = """
            {
                "token": "fcm-token"
            }
            """;

    @MockitoBean
    private DriverService service;

    @DisplayName("POST /api/v1/drivers/verify - 성공하면 토큰의 기사 번호로 검증하고 204")
    @Test
    void verifyDriver() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/verify").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).verify(eq(DRIVER_ID));
    }

    @DisplayName("POST /api/v1/drivers/verify - 토큰이 없으면 401")
    @Test
    void verifyDriver_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/verify"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UserErrorCode.UNAUTHORIZED.getCode()));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/verify - 토큰이 만료됐으면 401")
    @Test
    void verifyDriver_expiredToken() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.TOKEN_EXPIRED)).when(authService).authenticate(eq("expired-token"));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/verify").header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UserErrorCode.TOKEN_EXPIRED.getCode()));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/verify - 기사로 등록되지 않은 유저면 404")
    @Test
    void verifyDriver_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/verify").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/verify - 탈퇴한 기사면 404")
    @Test
    void verifyDriver_withdrawnDriver() throws Exception {

        /*
         * settings
         */
        Driver withdrawn = Driver.load(DRIVER_ID, USER_ID, "서울-1234-5678", null, null, null,
                DriverSetting.load(false), DriverStatus.VERIFIED, null, Instant.parse("2026-01-01T00:00:00Z"));

        doReturn(Optional.of(withdrawn)).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/verify").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/verify - 검증 대기 상태가 아니면 409")
    @Test
    void verifyDriver_notPending() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DriverErrorCode.DRIVER_NOT_PENDING)).when(service).verify(eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/verify").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_PENDING"));
    }

    @DisplayName("POST /api/v1/drivers/verify - 서비스에서 기사를 못 찾으면 404")
    @Test
    void verifyDriver_notFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DriverErrorCode.DRIVER_NOT_FOUND)).when(service).verify(eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/verify").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_FOUND"));
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 성공하면 토큰의 기사 번호를 담은 커맨드로 저장하고 204")
    @Test
    void registerVehicle() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_VEHICLE))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).registerVehicle(eq(new RegisterVehicleCommand(DRIVER_ID, 4, "12가3456", "중형")));
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 좌석 수의 최솟값 2 는 성공")
    @Test
    void registerVehicle_minSeats() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "seats": 2,
                                    "plateNumber": "12가3456",
                                    "type": "중형"
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(service).registerVehicle(eq(new RegisterVehicleCommand(DRIVER_ID, 2, "12가3456", "중형")));
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 좌석 수가 1 이면 400")
    @Test
    void registerVehicle_oneSeat() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "seats": 1,
                                    "plateNumber": "12가3456",
                                    "type": "중형"
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("seats")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 좌석 수가 없으면 400")
    @Test
    void registerVehicle_missingSeats() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "plateNumber": "12가3456",
                                    "type": "중형"
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("seats")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 좌석 수가 숫자가 아니면 400")
    @Test
    void registerVehicle_nonNumericSeats() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "seats": "abc",
                                    "plateNumber": "12가3456",
                                    "type": "중형"
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 차량 번호가 공백이면 400")
    @Test
    void registerVehicle_blankPlateNumber() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "seats": 4,
                                    "plateNumber": "   ",
                                    "type": "중형"
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("plateNumber")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 차량 번호가 없으면 400")
    @Test
    void registerVehicle_missingPlateNumber() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "seats": 4,
                                    "type": "중형"
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("plateNumber")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 차종이 공백이면 400")
    @Test
    void registerVehicle_blankType() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "seats": 4,
                                    "plateNumber": "12가3456",
                                    "type": ""
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("type")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 본문이 없으면 400")
    @Test
    void registerVehicle_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 토큰이 없으면 401")
    @Test
    void registerVehicle_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_VEHICLE))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 기사로 등록되지 않은 유저면 404")
    @Test
    void registerVehicle_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_VEHICLE))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 서비스에서 기사를 못 찾으면 404")
    @Test
    void registerVehicle_notFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DriverErrorCode.DRIVER_NOT_FOUND)).when(service)
                .registerVehicle(eq(new RegisterVehicleCommand(DRIVER_ID, 4, "12가3456", "중형")));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_VEHICLE))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_FOUND"));
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 도메인이 차량 번호를 거부하면 400")
    @Test
    void registerVehicle_invalidPlateNumber() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DriverErrorCode.INVALID_PLATE_NUMBER)).when(service)
                .registerVehicle(eq(new RegisterVehicleCommand(DRIVER_ID, 4, "12가3456", "중형")));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_VEHICLE))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLATE_NUMBER"));
    }

    @DisplayName("POST /api/v1/drivers/vehicle - 도메인이 좌석 수를 거부하면 400")
    @Test
    void registerVehicle_invalidSeats() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DriverErrorCode.INVALID_VEHICLE_SEATS)).when(service)
                .registerVehicle(eq(new RegisterVehicleCommand(DRIVER_ID, 4, "12가3456", "중형")));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/vehicle").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_VEHICLE))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VEHICLE_SEATS"));
    }

    @DisplayName("POST /api/v1/drivers/call - 성공하면 토큰의 기사 번호로 켜고 204")
    @Test
    void enableCall() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/call").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).enableCall(eq(DRIVER_ID));
    }

    @DisplayName("POST /api/v1/drivers/call - 토큰이 없으면 401")
    @Test
    void enableCall_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/call"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/call - 기사로 등록되지 않은 유저면 404")
    @Test
    void enableCall_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/call").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers/call - 서비스에서 기사를 못 찾으면 404")
    @Test
    void enableCall_notFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DriverErrorCode.DRIVER_NOT_FOUND)).when(service).enableCall(eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers/call").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_FOUND"));
    }

    @DisplayName("DELETE /api/v1/drivers/call - 성공하면 토큰의 기사 번호로 끄고 204")
    @Test
    void disableCall() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/drivers/call").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).disableCall(eq(DRIVER_ID));
    }

    @DisplayName("DELETE /api/v1/drivers/call - 토큰이 없으면 401")
    @Test
    void disableCall_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/drivers/call"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @DisplayName("DELETE /api/v1/drivers/call - 기사로 등록되지 않은 유저면 404")
    @Test
    void disableCall_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/drivers/call").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }

    @DisplayName("DELETE /api/v1/drivers/call - 서비스에서 기사를 못 찾으면 404")
    @Test
    void disableCall_notFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DriverErrorCode.DRIVER_NOT_FOUND)).when(service).disableCall(eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/drivers/call").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_FOUND"));
    }

    @DisplayName("GET /api/v1/drivers/me - 토큰의 유저 번호로 조회해 기사 정보를 돌려주고 기사 조회는 거치지 않는다")
    @Test
    void me() throws Exception {

        /*
         * settings
         */
        doReturn(new DriverResult(DRIVER_ID, USER_ID, "모여타기사", DriverStatus.VERIFIED, true)).when(service).getByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/drivers/me").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DRIVER_ID))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.name").value("모여타기사"))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.callEnabled").value(true));
        verify(service).getByUserId(eq(USER_ID));
        verifyNoInteractions(drivers);
    }

    @DisplayName("GET /api/v1/drivers/me - 닉네임이 없는 PENDING 기사면 name 은 null, 콜 꺼짐도 그대로 내려준다")
    @Test
    void me_pendingWithoutNickname() throws Exception {

        /*
         * settings
         */
        doReturn(new DriverResult(DRIVER_ID, USER_ID, null, DriverStatus.PENDING, false)).when(service).getByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/drivers/me").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(nullValue()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.callEnabled").value(false));
    }

    @DisplayName("GET /api/v1/drivers/me - 토큰이 없으면 401")
    @Test
    void me_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/drivers/me"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @DisplayName("GET /api/v1/drivers/me - Bearer 형식이 아닌 토큰이면 401")
    @Test
    void me_notBearerToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/drivers/me").header(HttpHeaders.AUTHORIZATION, "Basic test-access-token"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @DisplayName("GET /api/v1/drivers/me - 기사 미등록이면 404")
    @Test
    void me_notRegistered() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DriverErrorCode.DRIVER_NOT_REGISTERED)).when(service).getByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/drivers/me").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
    }

    @DisplayName("PUT /api/v1/drivers/fcm-token - 성공하면 토큰의 기사 번호와 FCM 토큰으로 저장하고 204")
    @Test
    void registerFcmToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(put("/api/v1/drivers/fcm-token").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FCM_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).registerFcmToken(eq(DRIVER_ID), eq("fcm-token"));
    }

    @DisplayName("PUT /api/v1/drivers/fcm-token - 토큰이 공백이면 400")
    @Test
    void registerFcmToken_blankToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(put("/api/v1/drivers/fcm-token").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "token": "   "
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("token")));
        verifyNoInteractions(service);
    }

    @DisplayName("PUT /api/v1/drivers/fcm-token - 토큰이 빈 문자열이면 400")
    @Test
    void registerFcmToken_emptyToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(put("/api/v1/drivers/fcm-token").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "token": ""
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("PUT /api/v1/drivers/fcm-token - 토큰이 null 이면 400")
    @Test
    void registerFcmToken_nullToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(put("/api/v1/drivers/fcm-token").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "token": null
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("PUT /api/v1/drivers/fcm-token - 본문이 없으면 400")
    @Test
    void registerFcmToken_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(put("/api/v1/drivers/fcm-token").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("PUT /api/v1/drivers/fcm-token - 토큰이 없으면 401")
    @Test
    void registerFcmToken_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(put("/api/v1/drivers/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FCM_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @DisplayName("PUT /api/v1/drivers/fcm-token - 기사로 등록되지 않은 유저면 404")
    @Test
    void registerFcmToken_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(put("/api/v1/drivers/fcm-token").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FCM_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }

    @DisplayName("PUT /api/v1/drivers/fcm-token - 도메인이 빈 토큰으로 거부하면 400")
    @Test
    void registerFcmToken_domainRejected() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DriverErrorCode.DRIVER_EMPTY_FCM_TOKEN)).when(service).registerFcmToken(eq(DRIVER_ID), eq("fcm-token"));

        /*
         * test
         */
        mockMvc.perform(put("/api/v1/drivers/fcm-token").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FCM_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DRIVER_EMPTY_FCM_TOKEN"));
    }

    @DisplayName("PUT /api/v1/drivers/fcm-token - 서비스에서 기사를 못 찾으면 404")
    @Test
    void registerFcmToken_notFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DriverErrorCode.DRIVER_NOT_FOUND)).when(service).registerFcmToken(eq(DRIVER_ID), eq("fcm-token"));

        /*
         * test
         */
        mockMvc.perform(put("/api/v1/drivers/fcm-token").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FCM_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_FOUND"));
    }

    @DisplayName("DELETE /api/v1/drivers/fcm-token - 성공하면 토큰의 기사 번호로 지우고 204")
    @Test
    void removeFcmToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/drivers/fcm-token").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).removeFcmToken(eq(DRIVER_ID));
    }

    @DisplayName("DELETE /api/v1/drivers/fcm-token - 토큰이 없으면 401")
    @Test
    void removeFcmToken_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/drivers/fcm-token"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @DisplayName("DELETE /api/v1/drivers/fcm-token - 기사로 등록되지 않은 유저면 404")
    @Test
    void removeFcmToken_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/drivers/fcm-token").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }

    @DisplayName("DELETE /api/v1/drivers/fcm-token - 서비스에서 기사를 못 찾으면 404")
    @Test
    void removeFcmToken_notFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DriverErrorCode.DRIVER_NOT_FOUND)).when(service).removeFcmToken(eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/drivers/fcm-token").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_FOUND"));
    }

    @DisplayName("POST /api/v1/drivers - 성공하면 토큰의 유저 번호를 담은 커맨드로 등록하고 결과를 200 으로 돌려준다")
    @Test
    void register() throws Exception {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, "서울-1234-5678", "국민은행", "123-456-789012", 4, "12가3456", "중형");

        doReturn(new DriverResult(DRIVER_ID, USER_ID, "모여타기사", DriverStatus.PENDING, true)).when(service).register(eq(command));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTER))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DRIVER_ID))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.name").value("모여타기사"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.callEnabled").value(true));
        verify(service).register(eq(command));
        verifyNoInteractions(drivers);  // 아직 기사가 아닌 유저가 부르는 API 라 @CurrentDriver 를 타면 안 된다
    }

    @DisplayName("POST /api/v1/drivers - 좌석 수의 최솟값 2 는 성공")
    @Test
    void register_minSeats() throws Exception {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, "서울-1234-5678", "국민은행", "123-456-789012", 2, "12가3456", "중형");

        doReturn(new DriverResult(DRIVER_ID, USER_ID, null, DriverStatus.PENDING, true)).when(service).register(eq(command));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": "서울-1234-5678",
                                    "bankName": "국민은행",
                                    "accountNumber": "123-456-789012",
                                    "vehicle": {
                                        "seats": 2,
                                        "plateNumber": "12가3456",
                                        "type": "중형"
                                    }
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(nullValue()));
        verify(service).register(eq(command));
    }

    @DisplayName("POST /api/v1/drivers - 토큰이 없으면 401")
    @Test
    void register_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTER))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 자격번호가 공백이면 400")
    @Test
    void register_blankQualificationNumber() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": " ",
                                    "bankName": "국민은행",
                                    "accountNumber": "123-456-789012",
                                    "vehicle": {
                                        "seats": 4,
                                        "plateNumber": "12가3456",
                                        "type": "중형"
                                    }
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("qualificationNumber")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 은행명이 없으면 400")
    @Test
    void register_missingBankName() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": "서울-1234-5678",
                                    "accountNumber": "123-456-789012",
                                    "vehicle": {
                                        "seats": 4,
                                        "plateNumber": "12가3456",
                                        "type": "중형"
                                    }
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("bankName")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 계좌번호가 빈 문자열이면 400")
    @Test
    void register_emptyAccountNumber() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": "서울-1234-5678",
                                    "bankName": "국민은행",
                                    "accountNumber": "",
                                    "vehicle": {
                                        "seats": 4,
                                        "plateNumber": "12가3456",
                                        "type": "중형"
                                    }
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("accountNumber")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 차량이 없으면 400 (toCommand 의 NPE 로 번지지 않는다)")
    @Test
    void register_missingVehicle() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": "서울-1234-5678",
                                    "bankName": "국민은행",
                                    "accountNumber": "123-456-789012"
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("vehicle")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 차량이 null 이면 400")
    @Test
    void register_nullVehicle() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": "서울-1234-5678",
                                    "bankName": "국민은행",
                                    "accountNumber": "123-456-789012",
                                    "vehicle": null
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("vehicle")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 차량 안의 좌석 수가 1 이면 400 (중첩 검증)")
    @Test
    void register_oneSeat() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": "서울-1234-5678",
                                    "bankName": "국민은행",
                                    "accountNumber": "123-456-789012",
                                    "vehicle": {
                                        "seats": 1,
                                        "plateNumber": "12가3456",
                                        "type": "중형"
                                    }
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("vehicle.seats")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 차량 안의 좌석 수가 없으면 400 (중첩 검증)")
    @Test
    void register_missingSeats() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": "서울-1234-5678",
                                    "bankName": "국민은행",
                                    "accountNumber": "123-456-789012",
                                    "vehicle": {
                                        "plateNumber": "12가3456",
                                        "type": "중형"
                                    }
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("vehicle.seats")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 차량 안의 차량 번호가 공백이면 400 (중첩 검증)")
    @Test
    void register_blankPlateNumber() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": "서울-1234-5678",
                                    "bankName": "국민은행",
                                    "accountNumber": "123-456-789012",
                                    "vehicle": {
                                        "seats": 4,
                                        "plateNumber": " ",
                                        "type": "중형"
                                    }
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("vehicle.plateNumber")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 차량 안의 차종이 없으면 400 (중첩 검증)")
    @Test
    void register_missingType() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": "서울-1234-5678",
                                    "bankName": "국민은행",
                                    "accountNumber": "123-456-789012",
                                    "vehicle": {
                                        "seats": 4,
                                        "plateNumber": "12가3456"
                                    }
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("vehicle.type")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 여러 필드가 틀리면 틀린 필드를 모두 메시지에 담아 400")
    @Test
    void register_multipleInvalidFields() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": "",
                                    "bankName": "",
                                    "accountNumber": "123-456-789012",
                                    "vehicle": {
                                        "seats": 4,
                                        "plateNumber": "12가3456",
                                        "type": "중형"
                                    }
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("qualificationNumber")))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("bankName")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 본문이 없으면 400")
    @Test
    void register_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - JSON 형식이 깨졌으면 400")
    @Test
    void register_malformedJson() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "qualificationNumber": "서울-1234-5678",
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/drivers - 이미 등록된 유저면 409")
    @Test
    void register_alreadyRegistered() throws Exception {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, "서울-1234-5678", "국민은행", "123-456-789012", 4, "12가3456", "중형");

        doThrow(new BusinessException(DriverErrorCode.DRIVER_ALREADY_REGISTERED)).when(service).register(eq(command));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTER))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_ALREADY_REGISTERED"));
    }

    @DisplayName("POST /api/v1/drivers - 도메인이 은행명을 거부하면 400")
    @Test
    void register_invalidBankName() throws Exception {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, "서울-1234-5678", "국민은행", "123-456-789012", 4, "12가3456", "중형");

        doThrow(new BusinessException(DriverErrorCode.INVALID_BANK_NAME)).when(service).register(eq(command));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTER))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BANK_NAME"));
    }

    @DisplayName("POST /api/v1/drivers - 도메인이 계좌번호를 거부하면 400")
    @Test
    void register_invalidAccountNumber() throws Exception {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, "서울-1234-5678", "국민은행", "123-456-789012", 4, "12가3456", "중형");

        doThrow(new BusinessException(DriverErrorCode.INVALID_ACCOUNT_NUMBER)).when(service).register(eq(command));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTER))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_NUMBER"));
    }

    @DisplayName("POST /api/v1/drivers - 도메인이 좌석 수를 거부하면 400")
    @Test
    void register_invalidSeats() throws Exception {

        /*
         * settings
         */
        RegisterDriverCommand command = new RegisterDriverCommand(USER_ID, "서울-1234-5678", "국민은행", "123-456-789012", 4, "12가3456", "중형");

        doThrow(new BusinessException(DriverErrorCode.INVALID_VEHICLE_SEATS)).when(service).register(eq(command));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/drivers").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTER))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VEHICLE_SEATS"));
    }
}
