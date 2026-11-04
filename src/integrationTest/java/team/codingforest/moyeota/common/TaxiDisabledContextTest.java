package team.codingforest.moyeota.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import team.codingforest.moyeota.driver.DriverService;
import team.codingforest.moyeota.driver.DriverController;
import team.codingforest.moyeota.matching.party.completion.ChatOnlyCompletionPolicy;
import team.codingforest.moyeota.matching.party.CompletedPartySweeper;
import team.codingforest.moyeota.matching.party.PartyRecruitingSweeper;
import team.codingforest.moyeota.matching.party.completion.PartyCompletionPolicy;

import static org.assertj.core.api.Assertions.*;

/**
 *  실사용자 릴리즈(택시 배차 없이 매칭+채팅만)는 TAXI_ENABLED=false 로 뜬다.
 *  입구(스케줄러·컨트롤러)만 빠지고, 다른 모듈이 주입받는 서비스는 남아 있어야 컨텍스트가 뜬다.
 */
@SpringBootTest(properties = "moyeota.taxi.enabled=false")
class TaxiDisabledContextTest {
    @Autowired ApplicationContext ctx;

    @Test
    void 택시_입구_빈은_등록되지_않는다() {
        assertThat(ctx.getBeanNamesForType(DriverController.class)).isEmpty();
    }

    /** 단위 테스트는 정책을 new 로 꽂으므로 정책 빈이 하나만 뜨는지는 여기서만 잡힌다 */
    @Test
    void 정원_충족_정책은_채팅만_정책_하나가_뜬다() {
        assertThat(ctx.getBeanNamesForType(ChatOnlyCompletionPolicy.class)).isNotEmpty();
        assertThat(ctx.getBeanNamesForType(PartyCompletionPolicy.class)).hasSize(1);
    }

    @Test
    void 방치된_방_자동_종료_스윕이_돈다() {
        assertThat(ctx.getBeanNamesForType(CompletedPartySweeper.class)).as("없으면 아무도 닫지 않은 방의 멤버가 영영 새 방에 못 들어간다").isNotEmpty();
        assertThat(ctx.getBeanNamesForType(PartyRecruitingSweeper.class)).isNotEmpty();
    }

    @Test
    void 다른_모듈이_의존하는_서비스는_남아_있다() {
        // PartyService 가 DriverAccess 를 주입받으므로 driver 서비스는 살아 있어야 한다
        assertThat(ctx.getBeanNamesForType(DriverService.class)).isNotEmpty();
    }
}
