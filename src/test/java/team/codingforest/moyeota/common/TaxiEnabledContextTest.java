package team.codingforest.moyeota.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import team.codingforest.moyeota.dispatch.application.DispatchListener;
import team.codingforest.moyeota.dispatch.application.MatchingSweeper;
import team.codingforest.moyeota.matching.application.ChatOnlyCompletionPolicy;
import team.codingforest.moyeota.matching.application.CompletedPartySweeper;
import team.codingforest.moyeota.matching.application.DispatchCompletionPolicy;
import team.codingforest.moyeota.matching.application.PartyCompletionPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  기본(발표) 모드 - 프로퍼티를 안 주면 matchIfMissing 으로 택시 기능이 켜진다.
 *  TaxiDisabledContextTest 의 반대편. 둘 다 있어야 조건이 서로 바뀐 실수가 양쪽에서 잡힌다.
 */
@SpringBootTest
@AutoConfigureMockMvc   // 시큐리티 필터 체인까지 태워야 permitAll 누락이 잡힌다
class TaxiEnabledContextTest {
    @Autowired ApplicationContext ctx;
    @Autowired MockMvc mvc;

    @Test
    void 정원_충족_정책은_배차_정책_하나가_뜬다() {
        assertThat(ctx.getBeanNamesForType(DispatchCompletionPolicy.class)).isNotEmpty();
        assertThat(ctx.getBeanNamesForType(ChatOnlyCompletionPolicy.class)).as("채팅만 정책이 뜨면 정원이 차도 콜이 안 나간다").isEmpty();
        assertThat(ctx.getBeanNamesForType(PartyCompletionPolicy.class)).hasSize(1);
    }

    @Test
    void 택시_입구_빈이_뜨고_방치_스윕은_뜨지_않는다() {
        assertThat(ctx.getBeanNamesForType(DispatchListener.class)).isNotEmpty();
        assertThat(ctx.getBeanNamesForType(MatchingSweeper.class)).isNotEmpty();
        assertThat(ctx.getBeanNamesForType(CompletedPartySweeper.class)).as("발표 모드에선 COMPLETED 가 순간이라 스윕할 게 없다").isEmpty();
    }

    /** 앱은 로그인 전에 이 값으로 화면을 고른다 - 정책 빈(matchIfMissing)과 @Value 기본값이 같은 쪽을 가리켜야 한다 */
    @Test
    void 앱_설정은_토큰_없이_taxiEnabled_true_를_돌려준다() throws Exception {
        mvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxiEnabled").value(true));
    }
}
