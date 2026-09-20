package team.codingforest.moyeota.matching.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import team.codingforest.moyeota.common.exception.BusinessException;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode.*;

class LettuceMatchingAdmissionTest {
    private final MatchingTransactions transactions = mock(MatchingTransactions.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final LettuceMatchingAdmission admission = new LettuceMatchingAdmission(transactions, redis);

    private void acquisition(boolean success) {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(success);
    }
    @Test
    void Redis_장애는_DB로_우회하지_않는다() {
        when(redis.opsForValue()).thenThrow(new IllegalStateException("연결 실패"));
        assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MATCHING_LOCK_UNAVAILABLE);
        verifyNoInteractions(transactions);
    }
    @Test
    void 대기시간이_끝나면_DB에_진입하거나_남의_잠금을_해제하지_않는다() {
        acquisition(false);
        assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MATCHING_BUSY);
        verifyNoInteractions(transactions);
        verify(redis, never()).execute(eq(LettuceMatchingAdmission.RELEASE), anyList(), anyString());
    }
    @Test
    void 대기중_인터럽트를_보존한다() {
        acquisition(false);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(call -> {
            Thread.currentThread().interrupt();
            return false;
        });
        try {
            assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(BusinessException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(transactions);
        } finally { Thread.interrupted(); }
    }
    @Test
    void 커밋후_해제하고_해제실패로_성공을_뒤집지_않는다() {
        acquisition(true);
        when(transactions.execute(any())).thenReturn("커밋 완료");
        when(redis.execute(eq(LettuceMatchingAdmission.RELEASE), anyList(), anyString())).thenThrow(new IllegalStateException());
        assertThat(admission.execute(1L, () -> "결과")).isEqualTo("커밋 완료");
        var order = inOrder(values, transactions, redis);
        order.verify(values).setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30)));
        order.verify(transactions).execute(any());
        order.verify(redis).execute(eq(LettuceMatchingAdmission.RELEASE), anyList(), anyString());
    }
    @Test
    void 해제실패가_롤백_원인을_덮지_않는다() {
        acquisition(true);
        when(transactions.execute(any())).thenThrow(new IllegalArgumentException("원래 실패"));
        when(redis.execute(eq(LettuceMatchingAdmission.RELEASE), anyList(), anyString())).thenThrow(new IllegalStateException());
        assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(IllegalArgumentException.class).hasMessage("원래 실패");
    }
}
