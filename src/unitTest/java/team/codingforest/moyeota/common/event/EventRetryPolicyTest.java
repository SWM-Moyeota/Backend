package team.codingforest.moyeota.common.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventRetryPolicyTest {
    private static final Instant T0 = Instant.parse("2026-09-29T10:00:00Z");

    @Test
    void 첫_실패_후_1분이_지나야_다시_시도한다() {
        assertThat(EventRetryPolicy.isDue(1, T0, null, T0.plusSeconds(59))).isFalse();
        assertThat(EventRetryPolicy.isDue(1, T0, null, T0.plusSeconds(60))).isTrue();
    }

    @Test
    void 시도가_늘수록_간격이_두_배가_된다() {
        Instant last = T0.plusSeconds(600);
        assertThat(EventRetryPolicy.isDue(2, T0, last, last.plusSeconds(119))).isFalse();   // 2분 대기
        assertThat(EventRetryPolicy.isDue(2, T0, last, last.plusSeconds(120))).isTrue();
    }

    @Test
    void 상한에_도달하면_더_이상_시도하지_않는다() {
        assertThat(EventRetryPolicy.isExhausted(EventRetryPolicy.MAX_ATTEMPTS)).isTrue();
        assertThat(EventRetryPolicy.isDue(EventRetryPolicy.MAX_ATTEMPTS, T0, T0, T0.plusSeconds(86_400))).isFalse();
    }
}
