package team.codingforest.moyeota.matching.route.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.matching.route.domain.RouteFinder;

import static org.assertj.core.api.Assertions.assertThat;

class DelayedRouteFinderTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(NaverDirectionsClient.class, () -> new NaverDirectionsClient(RestClient.create()))
            .withUserConfiguration(DelayedRouteFinder.class);

    @Test
    void loadtest_프로필에서는_네이버_대신_가짜를_쓴다() {
        // 네이버 클라이언트도 빈으로 남으므로 가짜가 우선되지 않으면 부하 테스트가 실제 네이버를 호출한다
        runner.withPropertyValues("spring.profiles.active=loadtest")
                .run(context -> assertThat(context.getBean(RouteFinder.class)).isInstanceOf(DelayedRouteFinder.class));
    }

    @Test
    void 프로필이_없으면_네이버를_쓴다() {
        // 운영에서 가짜가 뜨면 모든 방의 요금과 경로가 고정값이 된다
        runner.run(context -> assertThat(context.getBean(RouteFinder.class)).isInstanceOf(NaverDirectionsClient.class));
    }
}
