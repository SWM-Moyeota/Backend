package team.codingforest.moyeota.chat.room.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ChatEventRetryPolicyTest {
    private static final Instant T0 = Instant.parse("2026-09-29T10:00:00Z");

    @Test
    void 첫_실패_후_1분이_지나야_다시_시도한다() {
        assertThat(ChatEventRetryPolicy.isDue(1, T0, null, T0.plusSeconds(59))).isFalse();
        assertThat(ChatEventRetryPolicy.isDue(1, T0, null, T0.plusSeconds(60))).isTrue();
    }

    @Test
    void 시도가_늘수록_간격이_두_배가_된다() {
        Instant last = T0.plusSeconds(600);
        assertThat(ChatEventRetryPolicy.isDue(3, T0, last, last.plusSeconds(239))).isFalse();   // 4분 대기
        assertThat(ChatEventRetryPolicy.isDue(3, T0, last, last.plusSeconds(240))).isTrue();
    }

    @Test
    void 상한에_도달하면_더_이상_시도하지_않는다() {
        assertThat(ChatEventRetryPolicy.isExhausted(ChatEventRetryPolicy.MAX_ATTEMPTS)).isTrue();
        assertThat(ChatEventRetryPolicy.isDue(ChatEventRetryPolicy.MAX_ATTEMPTS, T0, T0, T0.plusSeconds(86_400))).isFalse();
    }
}