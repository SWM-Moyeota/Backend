package team.codingforest.moyeota.matching.noshow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  서버에 운영자 토큰(moyeota.operator.token)이 설정되지 않으면 운영자 API 는 꺼진다 - 어떤 헤더로도(빈 값 포함) 들어올 수 없다
 */
@WebMvcTest(value = OperatorNoShowObjectionController.class, properties = "moyeota.operator.token=")
class OperatorNoShowObjectionControllerDisabledUnitTest extends ControllerUnitTestSupport {

    @MockitoBean
    private NoShowObjectionService noShowObjectionService;

    @DisplayName("운영자 토큰 미설정 - 어떤 X-Operator-Token 을 보내도 401 USER006")
    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", " ", "anything", "null"})
    void disabled(String token) throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/operator/no-show-objections/{objectionId}/accept", 7L).header("X-Operator-Token", token))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER006"));
        verifyNoInteractions(noShowObjectionService);
    }
}
