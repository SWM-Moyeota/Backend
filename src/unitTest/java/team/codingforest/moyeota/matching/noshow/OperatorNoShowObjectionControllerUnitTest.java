package team.codingforest.moyeota.matching.noshow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionStatus;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionSummary;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  운영자 API 와 그 앞의 운영자 체인(X-Operator-Token). 시큐리티 필터 체인은 실제 SecurityConfig 그대로 탄다
 */
@WebMvcTest(value = OperatorNoShowObjectionController.class, properties = "moyeota.operator.token=" + OperatorNoShowObjectionControllerUnitTest.OPERATOR_TOKEN)
class OperatorNoShowObjectionControllerUnitTest extends ControllerUnitTestSupport {
    static final String OPERATOR_TOKEN = "test-operator-token";
    private static final String HEADER = "X-Operator-Token";
    private static final String OPERATOR_UNAUTHORIZED = "USER006";
    private static final String LIST_URL = "/api/v1/operator/no-show-objections";
    private static final String ACCEPT_URL = "/api/v1/operator/no-show-objections/{objectionId}/accept";
    private static final String REJECT_URL = "/api/v1/operator/no-show-objections/{objectionId}/reject";
    private static final Long OBJECTION_ID = 7L;
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T08:30:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-11-03T10:00:00Z");

    @MockitoBean
    private NoShowObjectionService noShowObjectionService;

    // ───────────────────────── 운영자 토큰 ─────────────────────────

    @DisplayName("운영자 토큰 - 헤더가 없으면 401 USER006 이고 컨트롤러까지 가지 않는다")
    @Test
    void noHeader() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(LIST_URL))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(OPERATOR_UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("운영자 인증이 필요합니다."));
        verifyNoInteractions(noShowObjectionService);
    }

    @DisplayName("운영자 토큰 - 틀리면(빈 값·한 글자 다름·앞부분만·뒤에 덧붙임) 401 USER006")
    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "test-operator-tokeN", "test-operator", "test-operator-token-extra"})
    void wrongToken(String token) throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(ACCEPT_URL, OBJECTION_ID).header(HEADER, token))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(OPERATOR_UNAUTHORIZED));
        verifyNoInteractions(noShowObjectionService);
    }

    @DisplayName("운영자 토큰 - 사용자 JWT(Bearer)로는 운영자 API 에 들어올 수 없다")
    @Test
    void userJwtOnly() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(ACCEPT_URL, OBJECTION_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(OPERATOR_UNAUTHORIZED));
        verifyNoInteractions(noShowObjectionService, authService);
    }

    @DisplayName("운영자 토큰 - 맞으면 Authorization 헤더 없이 컨트롤러까지 간다")
    @Test
    void correctToken() throws Exception {

        /*
         * settings
         */
        doReturn(List.of()).when(noShowObjectionService).findObjections(eq(ObjectionStatus.PENDING));

        /*
         * test
         */
        mockMvc.perform(get(LIST_URL).header(HEADER, OPERATOR_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isOk());
        verify(noShowObjectionService).findObjections(eq(ObjectionStatus.PENDING));
    }

    // ───────────────────────── GET 목록 ─────────────────────────

    @DisplayName("GET /api/v1/operator/no-show-objections - status 를 생략하면 PENDING 목록을 방 정보와 함께 준다")
    @Test
    void list() throws Exception {

        /*
         * settings
         */
        doReturn(List.of(new ObjectionSummary(OBJECTION_ID, 2L, 100L, "강남역", "판교역", MEETING_AT, "도착 버튼이 안 눌렸어요",
                ObjectionStatus.PENDING, CREATED_AT, null)))
                .when(noShowObjectionService).findObjections(eq(ObjectionStatus.PENDING));

        /*
         * test
         */
        mockMvc.perform(get(LIST_URL).header(HEADER, OPERATOR_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objections", hasSize(1)))
                .andExpect(jsonPath("$.objections[0].objectionId").value(OBJECTION_ID))
                .andExpect(jsonPath("$.objections[0].userId").value(2))
                .andExpect(jsonPath("$.objections[0].partyId").value(100))
                .andExpect(jsonPath("$.objections[0].departure").value("강남역"))
                .andExpect(jsonPath("$.objections[0].destination").value("판교역"))
                .andExpect(jsonPath("$.objections[0].meetingAt").value("2026-11-03T08:30:00Z"))
                .andExpect(jsonPath("$.objections[0].reason").value("도착 버튼이 안 눌렸어요"))
                .andExpect(jsonPath("$.objections[0].status").value("PENDING"))
                .andExpect(jsonPath("$.objections[0].createdAt").value("2026-11-03T10:00:00Z"))
                .andExpect(jsonPath("$.objections[0].resolvedAt").value(nullValue()));
    }

    @DisplayName("GET /api/v1/operator/no-show-objections - status 로 처리된 신청도 조회한다")
    @Test
    void list_byStatus() throws Exception {

        /*
         * settings
         */
        doReturn(List.of()).when(noShowObjectionService).findObjections(eq(ObjectionStatus.ACCEPTED));

        /*
         * test
         */
        mockMvc.perform(get(LIST_URL).param("status", "ACCEPTED").header(HEADER, OPERATOR_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objections", hasSize(0)));
        verify(noShowObjectionService).findObjections(eq(ObjectionStatus.ACCEPTED));
    }

    @DisplayName("GET /api/v1/operator/no-show-objections - 없는 status 면 400")
    @Test
    void list_invalidStatus() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(LIST_URL).param("status", "DONE").header(HEADER, OPERATOR_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(noShowObjectionService);
    }

    // ───────────────────────── accept / reject ─────────────────────────

    @DisplayName("POST /api/v1/operator/no-show-objections/{id}/accept - 인정하고 본문 없이 204")
    @Test
    void accept() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(ACCEPT_URL, OBJECTION_ID).header(HEADER, OPERATOR_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(noShowObjectionService).accept(eq(OBJECTION_ID));
    }

    @DisplayName("POST /api/v1/operator/no-show-objections/{id}/reject - 거절하고 본문 없이 204")
    @Test
    void reject() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(REJECT_URL, OBJECTION_ID).header(HEADER, OPERATOR_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(noShowObjectionService).reject(eq(OBJECTION_ID));
    }

    @DisplayName("POST accept - 이미 처리됐거나 없는 신청이면 409·404 를 code 와 함께 준다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = MatchingErrorCode.class, names = {"OBJECTION_ALREADY_RESOLVED", "OBJECTION_NOT_FOUND", "PARTY_NOT_FOUND"})
    void accept_businessError(MatchingErrorCode errorCode) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(noShowObjectionService).accept(eq(OBJECTION_ID));

        /*
         * test
         */
        mockMvc.perform(post(ACCEPT_URL, OBJECTION_ID).header(HEADER, OPERATOR_TOKEN))

        /*
         * verify
         */
                .andExpect(status().is(errorCode.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @DisplayName("POST reject - 이미 처리된 신청이면 409 OBJECTION_ALREADY_RESOLVED")
    @Test
    void reject_alreadyResolved() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.OBJECTION_ALREADY_RESOLVED)).when(noShowObjectionService).reject(eq(OBJECTION_ID));

        /*
         * test
         */
        mockMvc.perform(post(REJECT_URL, OBJECTION_ID).header(HEADER, OPERATOR_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OBJECTION_ALREADY_RESOLVED"));
    }

    @DisplayName("사용자 API 는 그대로 JWT 체인이 막는다 - 운영자 토큰으로는 사용자 API 에 들어올 수 없다")
    @Test
    void operatorTokenOnUserApi() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/no-shows").header(HEADER, OPERATOR_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
    }

    @DisplayName("운영자 토큰 - 헤더 이름은 대소문자를 가리지 않는다(HTTP 규약)")
    @Test
    void headerNameCaseInsensitive() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(REJECT_URL, OBJECTION_ID).header(HEADER.toLowerCase(), OPERATOR_TOKEN).header(HttpHeaders.ACCEPT, "application/json"))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(noShowObjectionService).reject(eq(OBJECTION_ID));
    }
}
