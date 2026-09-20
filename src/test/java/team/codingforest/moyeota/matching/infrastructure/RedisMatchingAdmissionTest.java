package team.codingforest.moyeota.matching.infrastructure;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import team.codingforest.moyeota.common.exception.BusinessException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode.*;

class RedisMatchingAdmissionTest {
    private final MatchingTransactions transactions = mock(MatchingTransactions.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RedissonClient> clients = mock(ObjectProvider.class);
    private final RedissonClient client = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final RedisMatchingAdmission admission = new RedisMatchingAdmission(transactions, clients);

    private void available() {
        when(clients.getObject()).thenReturn(client);
        when(client.getLock("moyeota:matching:member:1")).thenReturn(lock);
    }

    @Test
    void Redis_장애시_잠금없이_DB로_우회하지_않는다() {
        when(clients.getObject()).thenThrow(new IllegalStateException("연결 실패"));
        assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MATCHING_LOCK_UNAVAILABLE);
        verifyNoInteractions(transactions);
    }

    @Test
    void 락_대기_실패시_트랜잭션을_시작하거나_남의_락을_해제하지_않는다() throws Exception {
        available();
        when(lock.tryLock(2, TimeUnit.SECONDS)).thenReturn(false);
        assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MATCHING_BUSY);
        verifyNoInteractions(transactions);
        verify(lock, never()).unlock();
    }

    @Test
    void 인터럽트를_복구하고_트랜잭션을_시작하지_않는다() throws Exception {
        available();
        when(lock.tryLock(2, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        try {
            assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(BusinessException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(transactions);
        } finally { Thread.interrupted(); }
    }

    @Test
    void 커밋이_끝난_후에만_해제하며_해제실패는_성공을_뒤집지_않는다() throws Exception {
        available();
        when(lock.tryLock(2, TimeUnit.SECONDS)).thenReturn(true);
        when(transactions.execute(any())).thenReturn("커밋된 결과");
        doThrow(new IllegalMonitorStateException("이미 만료된 소유권")).when(lock).unlock();
        assertThat(admission.execute(1L, () -> "결과")).isEqualTo("커밋된 결과");
        var order = inOrder(lock, transactions);
        order.verify(lock).tryLock(2, TimeUnit.SECONDS);
        order.verify(transactions).execute(any());
        order.verify(lock).unlock();
    }

    @Test
    void 롤백_원인도_해제실패로_덮어쓰지_않는다() throws Exception {
        available();
        when(lock.tryLock(2, TimeUnit.SECONDS)).thenReturn(true);
        when(transactions.execute(any())).thenThrow(new IllegalArgumentException("원래 실패"));
        doThrow(new IllegalStateException("해제 실패")).when(lock).unlock();
        assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("원래 실패");
    }
}
