package team.codingforest.moyeota._config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 앱은 로그인 전에 이 값으로 화면을 고른다 - 승객 모드로 고정돼 늘 false 이고, 출시 앱이 읽으므로 키가 빠지면 안 된다.
 *
 * <p>버전 기준을 설정하지 않은 평시 상태를 확인한다. 값을 넣은 경우는 {@link AppConfigVersionGateUnitTest}.
 */
@WebMvcTest(AppConfigController.class)
class AppConfigControllerUnitTest extends ControllerUnitTestSupport {

    /** @WebMvcTest 슬라이스에는 설정 프로퍼티 빈이 올라오지 않는다 */
    @TestConfiguration
    @EnableConfigurationProperties(AppProperties.class)
    static class Properties {
    }

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

    @DisplayName("GET /api/v1/config - 버전 기준을 설정하지 않으면 두 키가 아예 빠진다")
    @Test
    void config_versionKeysOmittedWhenUnset() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/config"))

        /*
         * verify - 키가 있고 값이 null 이어도 앱은 통과시키지만, 평시 응답을 최소로 유지한다
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasKey("minVersionCode"))))
                .andExpect(jsonPath("$", not(hasKey("recommendedVersionCode"))));
    }
}
