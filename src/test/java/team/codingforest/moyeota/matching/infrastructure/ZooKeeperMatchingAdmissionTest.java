package team.codingforest.moyeota.matching.infrastructure;

import org.junit.jupiter.api.Test;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.springframework.beans.factory.ObjectProvider;
import team.codingforest.moyeota.common.exception.BusinessException;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode.*;

class ZooKeeperMatchingAdmissionTest {
    final MatchingTransactions transactions = mock(MatchingTransactions.class);
    @SuppressWarnings("unchecked") final ObjectProvider<ZooKeeperMatchingClient> clients = mock(ObjectProvider.class);
    final ZooKeeperMatchingClient client = mock(ZooKeeperMatchingClient.class);
    final InterProcessMutex lock = mock(InterProcessMutex.class);
    final ZooKeeperMatchingAdmission admission = new ZooKeeperMatchingAdmission(transactions, clients);
    void available() throws Exception {
        when(clients.getObject()).thenReturn(client);
        when(client.mutex(1L)).thenReturn(lock);
        when(lock.acquire(2, TimeUnit.SECONDS)).thenReturn(true);
    }
    @Test void 연결_장애시_DB로_우회하지_않는다() {
        when(clients.getObject()).thenThrow(new IllegalStateException());
        assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(MATCHING_LOCK_UNAVAILABLE);
        verifyNoInteractions(transactions);
    }
    @Test void 획득_실패시_DB진입과_해제를_하지_않는다() throws Exception {
        available(); when(lock.acquire(2, TimeUnit.SECONDS)).thenReturn(false);
        assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(MATCHING_BUSY);
        verifyNoInteractions(transactions); verify(lock, never()).release();
    }
    @Test void 인터럽트를_복구한다() throws Exception {
        available(); when(lock.acquire(2, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        try {
            assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(BusinessException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue(); verifyNoInteractions(transactions);
        } finally { Thread.interrupted(); }
    }
    @Test void 획득직후_소유권_불확실시_트랜잭션에_진입하지_않는다() throws Exception {
        available(); doThrow(new BusinessException(MATCHING_LOCK_UNAVAILABLE)).when(client).requireGeneration(0);
        assertThatThrownBy(() -> admission.execute(1L, () -> true)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(transactions); verify(lock).release();
    }
    @Test void 커밋후_해제실패는_성공을_뒤집지_않는다() throws Exception {
        available(); when(transactions.execute(any())).thenReturn("커밋 완료");
        doThrow(new IllegalStateException()).when(lock).release();
        assertThat(admission.execute(1L, () -> "결과")).isEqualTo("커밋 완료");
        var order = inOrder(lock, transactions);
        order.verify(lock).acquire(2, TimeUnit.SECONDS); order.verify(transactions).execute(any()); order.verify(lock).release();
    }
    @Test void 해제실패가_롤백_원인을_덮지_않는다() throws Exception {
        available(); when(transactions.execute(any())).thenThrow(new IllegalArgumentException("원래 실패"));
        doThrow(new IllegalStateException()).when(lock).release();
        assertThatThrownBy(() -> admission.execute(1L, () -> true)).hasMessage("원래 실패").isInstanceOf(IllegalArgumentException.class);
    }
}
