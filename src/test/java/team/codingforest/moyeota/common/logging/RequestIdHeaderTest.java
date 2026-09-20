package team.codingforest.moyeota.common.logging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  RequestIdFilter 가 시큐리티 필터보다 앞에 있어야 401 응답에도 X-Request-Id 가 붙는다.
 *  "로그인이 안 돼요" 문의도 앱이 받은 id 하나로 서버 로그를 찾을 수 있어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RequestIdHeaderTest {
    @Autowired MockMvc mvc;

    @Test
    void 정상_응답에_요청_id_헤더가_붙는다() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(LogFields.REQUEST_ID_HEADER));
    }

    @Test
    void 인증_실패_응답에도_요청_id_헤더가_붙는다() throws Exception {
        mvc.perform(get("/api/v1/users/me/favorite-places"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(LogFields.REQUEST_ID_HEADER));
    }

    @Test
    void 클라이언트가_보낸_요청_id를_그대로_돌려준다() throws Exception {
        mvc.perform(get("/health").header(LogFields.REQUEST_ID_HEADER, "app-7f3a9c2e-0001"))
                .andExpect(header().string(LogFields.REQUEST_ID_HEADER, "app-7f3a9c2e-0001"));
    }
}
