package team.codingforest.moyeota.matching.sse.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  heartbeat 주기와 프록시 타임아웃의 관계가 설계다. 주기가 타임아웃만큼 길어지면 ping 이 도착하기 직전에
 *  연결이 끊기는 경쟁이 생기는데, 앱이 3초 뒤 재연결하고 서버가 스냅샷을 다시 보내므로 <b>사용자에게 보이는
 *  고장이 아니다</b> - 증상 없이 SSE 의 효과만 깎인다. 그래서 여기서 고정한다.
 */
@SpringBootTest(classes = PartySseHeartbeatIntervalTest.Config.class)
class PartySseHeartbeatIntervalTest {

    /** CloudFront 오리진 응답 타임아웃 기본값. 이 앞단을 지나 앱이 api.moyeota.p-e.kr 로 붙는다 */
    private static final Duration ORIGIN_RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    @Configuration
    static class Config {
    }

    @Value("${moyeota.sse.heartbeat-ms}")
    private long heartbeatMs;

    @Test
    void 주기를_코드에_박지_않고_설정에서_읽는다() throws Exception {
        Method heartbeat = PartySseRegistry.class.getDeclaredMethod("heartbeat");
        Scheduled scheduled = heartbeat.getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedRateString()).isEqualTo("${moyeota.sse.heartbeat-ms}");
        assertThat(scheduled.fixedRate()).as("상수를 다시 박으면 환경별로 조정할 수 없다").isEqualTo(-1L);
    }

    @Test
    void 주기는_오리진_타임아웃의_절반_이하다() {
        assertThat(Duration.ofMillis(heartbeatMs))
                .as("프록시 타임아웃보다 짧지 않으면 heartbeat 가 아무 일도 하지 않는다")
                .isGreaterThan(Duration.ZERO)
                .isLessThanOrEqualTo(ORIGIN_RESPONSE_TIMEOUT.dividedBy(2));
    }
}
