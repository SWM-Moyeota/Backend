package team.codingforest.moyeota.chat.common.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.chat.common.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.common.exception.ChatException;
import team.codingforest.moyeota.chat.member.application.ChatRoomUserService;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  소켓 서버 전용 내부 API 와 그 앞의 체인. 방 확인은 X-Internal-Token, principal 은 사용자 JWT 로 막힌다
 *  시큐리티 필터 체인은 실제 SecurityConfig 그대로 탄다
 */
@WebMvcTest(value = ChatSocketInternalController.class, properties = "moyeota.internal.token=" + ChatSocketInternalControllerUnitTest.INTERNAL_TOKEN)
class ChatSocketInternalControllerUnitTest extends ControllerUnitTestSupport {
    static final String INTERNAL_TOKEN = "test-internal-token";
    private static final String HEADER = "X-Internal-Token";
    private static final String INTERNAL_UNAUTHORIZED = "USER007";
    private static final String PARTICIPANT_URL = "/internal/chat/chat-rooms/{chatRoomId}/users/{userId}";
    private static final String PRINCIPAL_URL = "/internal/chat/principal";
    private static final Long CHAT_ROOM_ID = 10L;
    private static final Long MEMBER_ID = 7L;

    @MockitoBean
    private ChatRoomUserService chatRoomUserService;

    // ───────────────────────── 방 참여 확인 ─────────────────────────

    @DisplayName("방 확인 - 내부 토큰이 맞고 참여 중이면 204")
    @Test
    void participant() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(PARTICIPANT_URL, CHAT_ROOM_ID, MEMBER_ID).header(HEADER, INTERNAL_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(chatRoomUserService).validateParticipant(MEMBER_ID, CHAT_ROOM_ID);
    }

    @DisplayName("방 확인 - 참여하지 않았으면 403 CHAT_NOT_PARTICIPANT")
    @Test
    void notParticipant() throws Exception {

        /*
         * settings
         */
        doThrow(new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                .when(chatRoomUserService).validateParticipant(MEMBER_ID, CHAT_ROOM_ID);

        /*
         * test
         */
        mockMvc.perform(get(PARTICIPANT_URL, CHAT_ROOM_ID, MEMBER_ID).header(HEADER, INTERNAL_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_PARTICIPANT"));
    }

    @DisplayName("방 확인 - 내부 토큰이 없으면 401 USER007 이고 컨트롤러까지 가지 않는다")
    @Test
    void participantWithoutToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(PARTICIPANT_URL, CHAT_ROOM_ID, MEMBER_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(INTERNAL_UNAUTHORIZED));
        verifyNoInteractions(chatRoomUserService);
    }

    @DisplayName("방 확인 - 사용자 JWT 만으로는 들어올 수 없다")
    @Test
    void participantWithUserTokenOnly() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(PARTICIPANT_URL, CHAT_ROOM_ID, MEMBER_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(INTERNAL_UNAUTHORIZED));
        verifyNoInteractions(chatRoomUserService);
    }

    // ───────────────────────── 연결 사용자 ─────────────────────────

    @DisplayName("principal - 사용자 JWT 의 주인을 userId·publicId 로 돌려준다")
    @Test
    void principal() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(PRINCIPAL_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.publicId").value(PUBLIC_ID.toString()));
    }

    @DisplayName("principal - 내부 토큰만으로는 들어올 수 없고 사용자 JWT 가 필요하다")
    @Test
    void principalWithInternalTokenOnly() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(PRINCIPAL_URL).header(HEADER, INTERNAL_TOKEN))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
    }
}
