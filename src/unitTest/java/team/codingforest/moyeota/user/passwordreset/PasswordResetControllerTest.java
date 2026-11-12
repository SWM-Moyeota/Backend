package team.codingforest.moyeota.user.passwordreset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.crypto.FieldHasher;
import team.codingforest.moyeota.user.passwordreset.application.*;
import team.codingforest.moyeota.user.passwordreset.domain.*;
import team.codingforest.moyeota.user.passwordreset.dto.*;
import team.codingforest.moyeota.user.passwordreset.presentation.PasswordResetController;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.context.annotation.Import(team.codingforest.moyeota.user.passwordreset.infrastructure.PasswordResetWebConfig.class)
@WebMvcTest(PasswordResetController.class)
class PasswordResetControllerTest extends ControllerUnitTestSupport {
    @MockitoBean PasswordResetSendService sends;
    @MockitoBean PasswordResetVerifyService verifies;
    @MockitoBean PasswordResetConfirmService confirms;
    @MockitoBean ResetRequests requests;
    @MockitoBean FieldHasher hasher;
    final String id = "a".repeat(43);
    final String root = "/api/v1/auth/password-reset";
    @Test void 비로그인_발송접수는_202와_접수번호를_반환한다() throws Exception {
        when(sends.send(any())).thenReturn(new PasswordResetSendResponse(id,180,60,"입력한 정보가 가입 정보와 일치하면 인증번호가 발송됩니다."));
        mockMvc.perform(post(root+"/send").contentType("application/json").content("""
                {"loginId":"hong123", "phoneNumber":"010-1234-5678"}
                """)).andExpect(status().isAccepted()).andExpect(jsonPath("$.requestId").value(id))
                .andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void 코드확인은_허가증을_반환한다() throws Exception {
        when(verifies.verify(any())).thenReturn(new PasswordResetVerifyResponse(id,600));
        mockMvc.perform(post(root+"/verify").contentType("application/json")
                .content("{\"requestId\":\""+id+"\",\"code\":\"012345\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resetToken").value(id));
    }
    @Test void 비밀번호저장_성공은_204다() throws Exception {
        mockMvc.perform(post(root+"/confirm").contentType("application/json").content("""
                {"resetToken":"%s","newPassword":"NewPassw0rd!","newPasswordConfirm":"NewPassw0rd!"}
                """.formatted(id))).andExpect(status().isNoContent());
        verify(confirms).confirm(any());
    }
    @Test void 잘못된코드는_서비스_호출전에_거절한다() throws Exception {
        mockMvc.perform(post(root+"/verify").contentType("application/json")
                .content("{\"requestId\":\""+id+"\",\"code\":\"abc\"}"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store"));
        verifyNoInteractions(verifies);
    }
    @Test void 제한응답은_대기시간을_포함한다() throws Exception {
        doThrow(new ResetRateLimited(45)).when(requests).checkClient(any());
        mockMvc.perform(post(root+"/send").contentType("application/json").content("{}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","45"));
    }
}
