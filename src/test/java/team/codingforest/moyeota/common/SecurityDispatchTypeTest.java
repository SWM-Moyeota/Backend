package team.codingforest.moyeota.common;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  SseEmitter 가 complete 되면 WAS 가 같은 요청을 ASYNC 디스패치로 필터 체인에 다시 태운다.
 *  그때는 인증 정보(1회차 스레드의 ThreadLocal)가 없어 인가 검사를 하면 실패하고, 응답은 이미 나가서 401 도 못 쓴다.
 *  ERROR 디스패치처럼 ASYNC 도 인가에서 빼야 한다. @CurrentUser 가 없는 상세 조회로 확인한다 - 인가만 통과하면 404 까지 간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityDispatchTypeTest {
    @Autowired MockMvc mvc;

    @Test
    void 처음_들어온_요청은_토큰이_없으면_401() throws Exception {
        mvc.perform(get("/api/v1/matching/rooms/999"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ASYNC_디스패치는_인가_검사_없이_통과한다() throws Exception {
        // 인가를 넘겼다는 증거 = 컨트롤러까지 가서 PARTY_NOT_FOUND 를 돌려준다
        mvc.perform(get("/api/v1/matching/rooms/999").with(dispatchedAs(DispatcherType.ASYNC)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ERROR_디스패치도_인가_검사_없이_통과한다() throws Exception {
        mvc.perform(get("/api/v1/matching/rooms/999").with(dispatchedAs(DispatcherType.ERROR)))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor dispatchedAs(DispatcherType type) {
        return request -> { request.setDispatcherType(type); return request; };
    }
}
