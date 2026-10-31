package team.codingforest.moyeota.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.report.exception.ReportErrorCode;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
class ReportControllerUnitTest extends ControllerUnitTestSupport {
    private static final String REPORT_URL = "/api/v1/reports";
    private static final String CALL_RESULT_URL = REPORT_URL + "/call-result";
    private static final Long PARTY_ID = 12L;
    private static final Long REPORT_ID = 100L;
    private static final Double LATITUDE = 37.4979;
    private static final Double LONGITUDE = 127.0276;
    private static final String REPORT_REQUEST = """
            {
                "partyId": 12,
                "latitude": 37.4979,
                "longitude": 127.0276
            }
            """;
    private static final String CALLED_REQUEST = """
            {
                "called": true
            }
            """;

    @MockitoBean
    private ReportService service;

    @DisplayName("POST /reports - 토큰의 USER_ID 와 요청 값으로 저장하고 reportId 를 반환")
    @Test
    void report() throws Exception {

        /*
         * settings
         */
        doReturn(REPORT_ID).when(service).report(eq(USER_ID), eq(PARTY_ID), eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        mockMvc.perform(post(REPORT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(REPORT_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(100));
        verify(service).report(eq(USER_ID), eq(PARTY_ID), eq(LATITUDE), eq(LONGITUDE));
    }

    @DisplayName("POST /reports - 위치가 없어도 null 그대로 넘겨 저장")
    @Test
    void report_noLocation() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "partyId": 12
                }
                """;

        doReturn(REPORT_ID).when(service).report(eq(USER_ID), eq(PARTY_ID), eq(null), eq(null));

        /*
         * test
         */
        mockMvc.perform(post(REPORT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(100));
        verify(service).report(eq(USER_ID), eq(PARTY_ID), eq(null), eq(null));
    }

    @DisplayName("POST /reports - 토큰이 없으면 401")
    @Test
    void report_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(REPORT_URL).contentType(MediaType.APPLICATION_JSON).content(REPORT_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /reports - 본문이 없으면 400")
    @Test
    void report_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(REPORT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /reports - partyId 가 숫자가 아니면 400")
    @Test
    void report_partyIdNotNumber() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "partyId": "abc"
                }
                """;

        /*
         * test
         */
        mockMvc.perform(post(REPORT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /reports - partyId 가 없으면 서비스의 REPORT_NOT_ALLOWED 가 403 으로 나간다")
    @Test
    void report_noPartyId() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "latitude": 37.4979,
                    "longitude": 127.0276
                }
                """;

        doThrow(new BusinessException(ReportErrorCode.REPORT_NOT_ALLOWED)).when(service).report(eq(USER_ID), eq(null), eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        mockMvc.perform(post(REPORT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_ALLOWED"));
    }

    @DisplayName("POST /reports - 운행 중 승객이 아니면 403")
    @Test
    void report_notAllowed() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(ReportErrorCode.REPORT_NOT_ALLOWED)).when(service).report(eq(USER_ID), eq(PARTY_ID), eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        mockMvc.perform(post(REPORT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(REPORT_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value(ReportErrorCode.REPORT_NOT_ALLOWED.getMessage()));
    }

    @DisplayName("PATCH /reports/call-result - 통화했으면 204 이고 USER_ID 와 true 가 서비스로 간다")
    @Test
    void confirmCall_called() throws Exception {

        /*
         * test
         */
        mockMvc.perform(patch(CALL_RESULT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(CALLED_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).confirmCall(eq(USER_ID), eq(true));
    }

    @DisplayName("PATCH /reports/call-result - 통화하지 않았으면 204 이고 false 가 서비스로 간다")
    @Test
    void confirmCall_notCalled() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "called": false
                }
                """;

        /*
         * test
         */
        mockMvc.perform(patch(CALL_RESULT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(service).confirmCall(eq(USER_ID), eq(false));
    }

    @DisplayName("PATCH /reports/call-result - 토큰이 없으면 401")
    @Test
    void confirmCall_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(patch(CALL_RESULT_URL).contentType(MediaType.APPLICATION_JSON).content(CALLED_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(service);
    }

    @DisplayName("PATCH /reports/call-result - called 가 없으면 검증 실패로 400")
    @Test
    void confirmCall_missingCalled() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                }
                """;

        /*
         * test
         */
        mockMvc.perform(patch(CALL_RESULT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", startsWith("called: ")));
        verifyNoInteractions(service);
    }

    @DisplayName("PATCH /reports/call-result - called 가 null 이면 검증 실패로 400")
    @Test
    void confirmCall_nullCalled() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "called": null
                }
                """;

        /*
         * test
         */
        mockMvc.perform(patch(CALL_RESULT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", startsWith("called: ")));
        verifyNoInteractions(service);
    }

    @DisplayName("PATCH /reports/call-result - called 가 불리언이 아니면 400")
    @Test
    void confirmCall_calledNotBoolean() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "called": "abc"
                }
                """;

        /*
         * test
         */
        mockMvc.perform(patch(CALL_RESULT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("PATCH /reports/call-result - 본문이 없으면 400")
    @Test
    void confirmCall_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(patch(CALL_RESULT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("PATCH /reports/call-result - 신고가 없으면 404")
    @Test
    void confirmCall_notFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(ReportErrorCode.REPORT_NOT_FOUND)).when(service).confirmCall(eq(USER_ID), eq(true));

        /*
         * test
         */
        mockMvc.perform(patch(CALL_RESULT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(CALLED_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @DisplayName("PATCH /reports/call-result - 이미 확정됐으면 409")
    @Test
    void confirmCall_alreadyConfirmed() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(ReportErrorCode.REPORT_ALREADY_CONFIRMED)).when(service).confirmCall(eq(USER_ID), eq(true));

        /*
         * test
         */
        mockMvc.perform(patch(CALL_RESULT_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(CALLED_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_CONFIRMED"));
    }
}
