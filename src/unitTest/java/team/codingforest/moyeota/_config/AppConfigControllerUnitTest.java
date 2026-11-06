package team.codingforest.moyeota._config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 앱은 로그인 전에 이 값으로 화면을 고른다 - 승객 모드로 고정돼 늘 false 이고, 출시 앱이 읽으므로 키가 빠지면 안 된다 */
@WebMvcTest(AppConfigController.class)
class AppConfigControllerUnitTest extends ControllerUnitTestSupport {

    @DisplayName("GET /api/v1/config - 토큰 없이 taxiEnabled false 를 키와 함께 준다")
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
                .andExpect(jsonPath("$", hasKey("taxiEnabled")))
                .andExpect(jsonPath("$.taxiEnabled").value(false));
    }
}
