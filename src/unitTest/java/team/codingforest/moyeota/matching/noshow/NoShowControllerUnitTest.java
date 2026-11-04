package team.codingforest.moyeota.matching.noshow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.noshow.domain.NoShowHistory;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionStatus;

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

@WebMvcTest(NoShowController.class)
class NoShowControllerUnitTest extends ControllerUnitTestSupport {
    private static final String LIST_URL = "/api/v1/matching/no-shows";
    private static final String OBJECTION_URL = "/api/v1/matching/rooms/{partyId}/no-show-objection";
    private static final Long PARTY_ID = 100L;
    private static final Long OLDER_PARTY_ID = 90L;
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T08:30:00Z");
    private static final Instant NO_SHOW_AT = Instant.parse("2026-11-03T08:40:00Z");
    private static final Instant OLDER_MEETING_AT = Instant.parse("2026-10-20T08:00:00Z");
    private static final Instant OLDER_NO_SHOW_AT = Instant.parse("2026-10-20T08:10:00Z");
    private static final Instant VOIDED_AT = Instant.parse("2026-10-21T09:00:00Z");
    private static final String REASON = "도착 버튼이 눌리지 않았습니다";

    @MockitoBean
    private NoShowObjectionService noShowObjectionService;

    // ───────────────────────── GET /no-shows ─────────────────────────

    @DisplayName("GET /api/v1/matching/no-shows - 토큰의 사용자 노쇼 기록을 약속 시간·무효 여부·이의 상태와 함께 준다")
    @Test
    void findMyNoShows() throws Exception {

        /*
         * settings
         */
        doReturn(List.of(
                new NoShowHistory(PARTY_ID, "강남역", "판교역", MEETING_AT, NO_SHOW_AT, null, null),
                new NoShowHistory(OLDER_PARTY_ID, "서울역", "시청", OLDER_MEETING_AT, OLDER_NO_SHOW_AT, VOIDED_AT, ObjectionStatus.ACCEPTED)))
                .when(noShowObjectionService).findMyNoShows(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(LIST_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noShows", hasSize(2)))
                .andExpect(jsonPath("$.noShows[0].partyId").value(PARTY_ID))
                .andExpect(jsonPath("$.noShows[0].departure").value("강남역"))
                .andExpect(jsonPath("$.noShows[0].destination").value("판교역"))
                .andExpect(jsonPath("$.noShows[0].meetingAt").value("2026-11-03T08:30:00Z"))
                .andExpect(jsonPath("$.noShows[0].noShowAt").value("2026-11-03T08:40:00Z"))
                .andExpect(jsonPath("$.noShows[0].voided").value(false))
                .andExpect(jsonPath("$.noShows[0].objectionStatus").value(nullValue()))
                .andExpect(jsonPath("$.noShows[1].partyId").value(OLDER_PARTY_ID))
                .andExpect(jsonPath("$.noShows[1].voided").value(true))
                .andExpect(jsonPath("$.noShows[1].objectionStatus").value("ACCEPTED"));
    }

    @DisplayName("GET /api/v1/matching/no-shows - 기록이 없으면 빈 배열")
    @Test
    void findMyNoShows_empty() throws Exception {

        /*
         * settings
         */
        doReturn(List.of()).when(noShowObjectionService).findMyNoShows(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(LIST_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noShows", hasSize(0)));
    }

    @DisplayName("GET /api/v1/matching/no-shows - 토큰이 없으면 401")
    @Test
    void findMyNoShows_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(LIST_URL))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(noShowObjectionService);
    }

    // ───────────────────────── POST /no-show-objection ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/no-show-objection - 토큰의 사용자로 이의를 내고 본문 없이 201")
    @Test
    void object() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(OBJECTION_URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"" + REASON + "\"}"))

        /*
         * verify
         */
                .andExpect(status().isCreated())
                .andExpect(content().string(""));
        verify(noShowObjectionService).object(eq(PARTY_ID), eq(USER_ID), eq(REASON));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/no-show-objection - 사유가 없거나 공백이면 400")
    @Test
    void object_blankReason() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(OBJECTION_URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(post(OBJECTION_URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content("{}"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(noShowObjectionService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/no-show-objection - 사유가 500자를 넘으면 400")
    @Test
    void object_tooLongReason() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(OBJECTION_URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"" + "가".repeat(501) + "\"}"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(noShowObjectionService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/no-show-objection - 서비스 예외를 상태 코드와 code 로 돌려준다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = MatchingErrorCode.class, names = {"NOT_PARTY_MEMBER", "PARTY_NOT_FOUND", "NOT_NO_SHOW", "ALREADY_OBJECTED"})
    void object_businessError(MatchingErrorCode errorCode) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(noShowObjectionService).object(eq(PARTY_ID), eq(USER_ID), eq(REASON));

        /*
         * test
         */
        mockMvc.perform(post(OBJECTION_URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"" + REASON + "\"}"))

        /*
         * verify
         */
                .andExpect(status().is(errorCode.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/no-show-objection - 토큰이 없으면 401")
    @Test
    void object_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(OBJECTION_URL, PARTY_ID).contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"" + REASON + "\"}"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(noShowObjectionService);
    }
}
