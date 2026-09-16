package team.codingforest.moyeota.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import team.codingforest.moyeota.dispatch.application.DispatchListener;
import team.codingforest.moyeota.dispatch.application.DispatchService;
import team.codingforest.moyeota.dispatch.application.MatchingSweeper;
import team.codingforest.moyeota.dispatch.presentation.DispatchCallController;
import team.codingforest.moyeota.dispatch.presentation.DriverLocationController;
import team.codingforest.moyeota.dispatch.presentation.RideController;
import team.codingforest.moyeota.driver.application.DriverApplicationService;
import team.codingforest.moyeota.driver.presentation.DriverController;
import team.codingforest.moyeota.matching.application.ChatOnlyCompletionPolicy;
import team.codingforest.moyeota.matching.application.CompletedPartySweeper;
import team.codingforest.moyeota.matching.application.DispatchCompletionPolicy;
import team.codingforest.moyeota.matching.application.PartyCompletionPolicy;

import static org.assertj.core.api.Assertions.*;

/**
 *  실사용자 릴리즈(택시 배차 없이 매칭+채팅만)는 TAXI_ENABLED=false 로 뜬다.
 *  입구(리스너·스케줄러·컨트롤러)만 빠지고, 다른 모듈이 주입받는 서비스는 남아 있어야 컨텍스트가 뜬다.
 */
@SpringBootTest(properties = "moyeota.taxi.enabled=false")
class TaxiDisabledContextTest {
    @Autowired ApplicationContext ctx;

    @Test
    void 택시_입구_빈은_등록되지_않는다() {
        assertThat(ctx.getBeanNamesForType(DispatchListener.class)).as("정원 차도 콜을 뿌리면 안 된다").isEmpty();
        assertThat(ctx.getBeanNamesForType(MatchingSweeper.class)).as("타임아웃 해산이 돌면 모든 방이 사라진다").isEmpty();
        assertThat(ctx.getBeanNamesForType(DispatchCallController.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(DriverLocationController.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(RideController.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(DriverController.class)).isEmpty();
    }

    /** 단위 테스트는 정책을 new 로 꽂으므로 @ConditionalOnProperty 가 뒤바뀐 실수는 여기서만 잡힌다 */
    @Test
    void 정원_충족_정책은_채팅만_정책_하나가_뜬다() {
        assertThat(ctx.getBeanNamesForType(DispatchCompletionPolicy.class)).as("배차 정책이 뜨면 정원 찬 방이 MATCHING 에 갇힌다").isEmpty();
        assertThat(ctx.getBeanNamesForType(ChatOnlyCompletionPolicy.class)).isNotEmpty();
        assertThat(ctx.getBeanNamesForType(PartyCompletionPolicy.class)).hasSize(1);
    }

    @Test
    void 방치된_방_자동_종료_스윕이_돈다() {
        assertThat(ctx.getBeanNamesForType(CompletedPartySweeper.class)).as("없으면 아무도 닫지 않은 방의 멤버가 영영 새 방에 못 들어간다").isNotEmpty();
    }

    @Test
    void 다른_모듈이_의존하는_서비스는_남아_있다() {
        // ReportApplicationService·PartyApplicationService 가 DriverAccess 를 주입받으므로 driver 서비스는 살아 있어야 한다
        assertThat(ctx.getBeanNamesForType(DriverApplicationService.class)).isNotEmpty();
        assertThat(ctx.getBeanNamesForType(DispatchService.class)).isNotEmpty();
    }
}
