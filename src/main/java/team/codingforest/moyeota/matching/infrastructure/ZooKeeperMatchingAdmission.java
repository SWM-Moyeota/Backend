package team.codingforest.moyeota.matching.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.MatchingAdmission;
import static team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@Slf4j
public class ZooKeeperMatchingAdmission implements MatchingAdmission {
    private final MatchingTransactions transactions;
    private final ObjectProvider<ZooKeeperMatchingClient> clients;

    public ZooKeeperMatchingAdmission(MatchingTransactions transactions, ObjectProvider<ZooKeeperMatchingClient> clients) {
        this.transactions = transactions;
        this.clients = clients;
    }

    @Override public <T> T execute(Long memberId, Supplier<T> operation) {
        MatchingTransactions.requireNoTransaction();
        ZooKeeperMatchingClient client;
        InterProcessMutex lock;
        long generation;
        try {
            client = clients.getObject();
            generation = client.awaitGeneration();
            lock = client.mutex(memberId);
            if (!lock.acquire(2, TimeUnit.SECONDS)) throw new BusinessException(MATCHING_BUSY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(MATCHING_BUSY);
        } catch (BusinessException e) { throw e;
        } catch (Exception e) { throw new BusinessException(MATCHING_LOCK_UNAVAILABLE); }

        try {
            client.requireGeneration(generation);
            return transactions.execute(() -> {
                client.requireGeneration(generation);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) { client.requireGeneration(generation); }
                });
                return operation.get();
            });
        } catch (PessimisticLockingFailureException | QueryTimeoutException | TransactionTimedOutException e) {
            throw new BusinessException(MATCHING_BUSY);
        } finally {
            try { lock.release(); }
            catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("매칭 ZooKeeper 잠금 해제 실패 memberId={} errorType={}", memberId, e.getClass().getSimpleName());
            }
        }
    }
}
