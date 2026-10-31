package team.codingforest.moyeota._config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정책 빈은 채팅만(TaxiDisabledContextTest)인데 앱에는 true 가 나가면 대기 화면이 오지 않는 택시를 기다린다 */
@WebMvcTest(controllers = AppConfigController.class, properties = "moyeota.taxi.enabled=false")
class AppConfigControllerTaxiDisabledUnitTest extends ControllerUnitTestSupport {

    @DisplayName("GET /api/v1/config - moyeota.taxi.enabled=false 면 토큰 없이 taxiEnabled false")
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
                .andExpect(jsonPath("$.taxiEnabled").value(false));
    }
}
