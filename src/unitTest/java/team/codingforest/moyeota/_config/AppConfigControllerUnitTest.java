package team.codingforest.moyeota._config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 앱은 로그인 전에 이 값으로 화면을 고른다 - 정책 빈(matchIfMissing, TaxiEnabledContextTest)과 @Value 기본값이 같은 쪽을 가리켜야 한다 */
@WebMvcTest(AppConfigController.class)
class AppConfigControllerUnitTest extends ControllerUnitTestSupport {

    @DisplayName("GET /api/v1/config - 프로퍼티가 없으면 토큰 없이 taxiEnabled true")
    @Test
    void config() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/config"))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxiEnabled").value(true));
    }
}
