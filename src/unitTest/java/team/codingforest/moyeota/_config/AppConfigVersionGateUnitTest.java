package team.codingforest.moyeota._config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 환경변수로 버전 기준을 넣은 상태. 앱은 이 두 값으로 업데이트 화면을 띄울지 정한다 - 차단이 권장보다 먼저다.
 *
 * <p>값을 넣지 않은 평시 상태는 {@link AppConfigControllerUnitTest}.
 */
@WebMvcTest(AppConfigController.class)
@TestPropertySource(properties = {
        "moyeota.app.min-version-code=26110501",
        "moyeota.app.recommended-version-code=26110502"
})
class AppConfigVersionGateUnitTest extends ControllerUnitTestSupport {

    @TestConfiguration
    @EnableConfigurationProperties(AppProperties.class)
    static class Properties {
    }

    @DisplayName("GET /api/v1/config - 설정한 버전 기준을 그대로 정수로 내려준다")
    @Test
    void config_versionCodes() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/config"))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxiEnabled").value(false))
                .andExpect(jsonPath("$.minVersionCode").value(26110501))
                .andExpect(jsonPath("$.recommendedVersionCode").value(26110502));
    }
}
