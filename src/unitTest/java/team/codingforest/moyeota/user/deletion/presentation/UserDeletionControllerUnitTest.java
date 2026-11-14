package team.codingforest.moyeota.user.deletion.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.deletion.application.UserDeletionService;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  회원 탈퇴의 요청 모양. 소셜 회원은 비밀번호가 없어 본문 없이 부르므로, 본문이 없어도 서비스까지 가야 한다.
 *  비밀번호가 꼭 필요한지는 회원 종류를 아는 서비스가 판단한다
 */
@WebMvcTest(UserDeletionController.class)
class UserDeletionControllerUnitTest extends ControllerUnitTestSupport {
    private static final String URL = "/api/v1/users/me";

    @MockitoBean
    private UserDeletionService userDeletionService;

    @DisplayName("DELETE /api/v1/users/me - 비밀번호를 본문으로 보내면 그대로 넘긴다(아이디·비밀번호 회원)")
    @Test
    void delete_withPassword() throws Exception {
        mockMvc.perform(delete(URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"Passw0rd!\"}"))
                .andExpect(status().isNoContent());

        verify(userDeletionService).delete(USER_ID, "Passw0rd!");
    }

    @DisplayName("DELETE /api/v1/users/me - 본문이 없어도 400 이 아니라 서비스까지 간다(소셜 회원)")
    @Test
    void delete_withoutBody() throws Exception {
        mockMvc.perform(delete(URL).with(loginUser()))
                .andExpect(status().isNoContent());

        verify(userDeletionService).delete(USER_ID, null);
    }

    @DisplayName("DELETE /api/v1/users/me - 빈 객체를 보내도 서비스까지 간다(소셜 회원)")
    @Test
    void delete_withEmptyObject() throws Exception {
        mockMvc.perform(delete(URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());

        verify(userDeletionService).delete(USER_ID, null);
    }

    @DisplayName("DELETE /api/v1/users/me - 아이디·비밀번호 회원이 비밀번호를 빼면 400 INVALID_REQUEST 다(기존 계약 유지)")
    @Test
    void delete_localWithoutPassword() throws Exception {
        doThrow(new IllegalArgumentException("비밀번호는 필수입니다.")).when(userDeletionService).delete(USER_ID, null);

        mockMvc.perform(delete(URL).with(loginUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @DisplayName("DELETE /api/v1/users/me - 비밀번호가 틀리면 400 PASSWORD_MISMATCH(USER111) 다")
    @Test
    void delete_passwordMismatch() throws Exception {
        doThrow(new UserException(UserErrorCode.PASSWORD_MISMATCH)).when(userDeletionService).delete(USER_ID, "wrong");

        mockMvc.perform(delete(URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER111"));
    }

    @DisplayName("DELETE /api/v1/users/me - 토큰이 없으면 401 이고 서비스까지 가지 않는다")
    @Test
    void delete_unauthenticated() throws Exception {
        mockMvc.perform(delete(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDeletionService);
    }
}
