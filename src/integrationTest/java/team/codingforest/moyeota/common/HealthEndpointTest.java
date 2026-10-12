package team.codingforest.moyeota.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  CodeDeploy ValidateService(scripts/validate.sh)가 토큰 없이 GET /health 200 을 기대한다.
 *  actuator 경로(base-path)·시큐리티 화이트리스트·show-details 세 설정이 같이 맞아야 통과한다.
 */
@SpringBootTest
@AutoConfigureMockMvc   // 시큐리티 필터 체인까지 태운 MockMvc
class HealthEndpointTest {
    @Autowired MockMvc mvc;

    @Test
    void 토큰_없이_health를_부르면_200_UP이다() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());   // 외부에 DB·Redis 상세가 새면 안 된다
    }

    @Test
    void 기본_actuator_경로는_열려_있지_않다() throws Exception {
        // base-path 를 / 로 옮겼으니 /actuator/health 는 더 이상 200 이 아니어야 한다
        mvc.perform(get("/actuator/health")).andExpect(status().is4xxClientError());
    }
}
